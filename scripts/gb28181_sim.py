#!/usr/bin/env python3
"""Minimal GB28181 IPC simulator for the demo machine (classmate A).

REGISTER (Digest qop=auth) + Keepalive + Catalog/DeviceInfo/DeviceStatus
+ INVITE → ffmpeg MPEG-2 PS (vob) → RTP payload 96 to ZLM.
Stdlib only; ffmpeg must be on PATH.
"""
from __future__ import annotations

import argparse
import hashlib
import os
import random
import re
import signal
import socket
import struct
import subprocess
import sys
import threading
import time
import uuid

# 多少次 ECONNREFUSED 才认定 ZLM 的收流端口真的没了。
# 不能要求「连续」：内核对 ICMP port unreachable 限速，实测只有约一半的 send
# 会抛出来，连续计数永远凑不满，模拟器就会一直朝空端口推流。改成窗口内累计。
RTP_REFUSED_LIMIT = 20
RTP_REFUSED_WINDOW_S = 3.0


def md5_hex(text: str) -> str:
    return hashlib.md5(text.encode("utf-8")).hexdigest()


def sip_header(msg: str, name: str) -> str:
    m = re.search(rf"(?im)^{re.escape(name)}\s*:\s*([^\r\n]*)", msg)
    return m.group(1).strip() if m else ""


def sip_headers(msg: str, name: str) -> list[str]:
    """取出某个头的全部取值，绝不能带上行尾的 \\r。

    不要写成 `(.+)$`：Python 在 MULTILINE 下 `$` 匹配在 \\n 之前，`.` 又能吃 \\r，
    于是捕获值会带一个尾随 \\r。回抄进应答的 Via 就变成 `\\r\\r\\n`，WVP 的
    GBStringMsgParser 把多出来的空行当成头部区结束，From/To/CSeq/Content-Length
    全部丢失，getCSeqHeader() 为空并抛 NullPointerException——整条应答被丢弃。
    后果是 WVP 收不到 INVITE 的 200 OK，60 秒后按「-1024 消息超时未回复」拆流，
    画面正好冻在最后一帧。
    """
    return [v.strip() for v in re.findall(rf"(?im)^{re.escape(name)}\s*:\s*([^\r\n]*)", msg)]


def xml_tag(body: str, tag: str) -> str:
    m = re.search(rf"<{tag}>([^<]*)</{tag}>", body, re.I)
    return m.group(1).strip() if m else ""


class Gb28181Sim:
    def __init__(self, args: argparse.Namespace) -> None:
        self.server_ip = args.server_ip
        self.server_port = args.server_port
        self.device_id = args.device_id
        self.channel_id = args.channel_id
        self.domain = args.domain
        self.password = args.password
        self.local_ip = args.local_ip
        self.local_port = args.local_port
        self.video = args.video
        self.expires = args.expires
        self.keepalive_sec = args.keepalive
        self.mux = args.mux
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            self.sock.bind((self.local_ip, self.local_port))
        except OSError:
            self.sock.bind(("0.0.0.0", self.local_port))
        self.sock.settimeout(0.5)
        self.call_id = str(uuid.uuid4())
        self.tag = uuid.uuid4().hex[:8]
        self.from_tag = uuid.uuid4().hex[:8]
        self.cseq = 1
        self.sn = 1
        self.ffmpeg: subprocess.Popen | None = None
        self.pump: threading.Thread | None = None
        self.registered = threading.Event()
        self.quit = threading.Event()
        self.media_stop = threading.Event()
        self.closed = False

    def log(self, msg: str) -> None:
        print(time.strftime("%H:%M:%S"), msg, flush=True)

    def send_sip(self, headers: list[str], body: bytes = b"") -> None:
        lines = list(headers) + [f"Content-Length: {len(body)}", "", ""]
        pkt = "\r\n".join(lines[:-1]).encode("utf-8") + b"\r\n" + body
        self.sock.sendto(pkt, (self.server_ip, self.server_port))

    def contact(self) -> str:
        return f"<sip:{self.device_id}@{self.local_ip}:{self.local_port}>"

    def via(self) -> str:
        return (
            f"SIP/2.0/UDP {self.local_ip}:{self.local_port};rport;"
            f"branch=z9hG4bK{uuid.uuid4().hex[:12]}"
        )

    def common_headers(self, method: str, request_uri: str, to_uri: str, extra: list[str] | None = None) -> list[str]:
        self.cseq += 1
        lines = [
            f"{method} {request_uri} SIP/2.0",
            f"Via: {self.via()}",
            f"From: <sip:{self.device_id}@{self.domain}>;tag={self.from_tag}",
            f"To: <{to_uri}>",
            f"Call-ID: {self.call_id if method == 'REGISTER' else str(uuid.uuid4())}",
            f"CSeq: {self.cseq} {method}",
            f"Contact: {self.contact()}",
            "Max-Forwards: 70",
            "User-Agent: easySVA-gb-sim",
        ]
        if extra:
            lines.extend(extra)
        return lines

    def digest_auth(self, challenge: str, method: str, uri: str) -> str:
        realm = re.search(r'realm="([^"]+)"', challenge, re.I)
        nonce = re.search(r'nonce="([^"]+)"', challenge, re.I)
        qop_m = re.search(r"qop=\"?([^\",]+)", challenge, re.I)
        realm_v = realm.group(1) if realm else self.domain
        nonce_v = nonce.group(1) if nonce else ""
        qop = (qop_m.group(1) if qop_m else "").strip()
        ha1 = md5_hex(f"{self.device_id}:{realm_v}:{self.password}")
        ha2 = md5_hex(f"{method}:{uri}")
        if qop.lower() == "auth":
            nc = "00000001"
            cnonce = uuid.uuid4().hex[:16]
            resp = md5_hex(f"{ha1}:{nonce_v}:{nc}:{cnonce}:{qop}:{ha2}")
            return (
                f'Digest username="{self.device_id}", realm="{realm_v}", '
                f'nonce="{nonce_v}", uri="{uri}", qop={qop}, nc={nc}, '
                f'cnonce="{cnonce}", response="{resp}", algorithm=MD5'
            )
        resp = md5_hex(f"{ha1}:{nonce_v}:{ha2}")
        return (
            f'Digest username="{self.device_id}", realm="{realm_v}", '
            f'nonce="{nonce_v}", uri="{uri}", response="{resp}", algorithm=MD5'
        )

    def send_register(self, auth: str | None = None, expires: int | None = None) -> None:
        uri = f"sip:{self.server_ip}:{self.server_port}"
        exp = self.expires if expires is None else expires
        extra = [f"Expires: {exp}"]
        if auth:
            extra.append(f"Authorization: {auth}")
        headers = self.common_headers("REGISTER", uri, f"sip:{self.device_id}@{self.domain}", extra)
        # REGISTER keeps a stable Call-ID; common_headers overwrote it for non-REGISTER only
        headers[4] = f"Call-ID: {self.call_id}"
        self.send_sip(headers)

    def unregister(self) -> None:
        if not self.registered.is_set():
            return
        uri = f"sip:{self.server_ip}:{self.server_port}"
        self.send_register(expires=0)
        self.log("UNREGISTER Expires=0")
        deadline = time.time() + 3.0
        while time.time() < deadline:
            try:
                data, _addr = self.sock.recvfrom(65535)
            except socket.timeout:
                continue
            except OSError:
                break
            text = data.decode("utf-8", "replace")
            if text.startswith("SIP/2.0 401") or text.startswith("SIP/2.0 407"):
                chal = sip_header(text, "WWW-Authenticate") or sip_header(text, "Proxy-Authenticate")
                auth = self.digest_auth(chal, "REGISTER", uri)
                self.send_register(auth, expires=0)
                self.log("UNREGISTER with Digest")
            elif text.startswith("SIP/2.0 200"):
                cseq = sip_header(text, "CSeq") or ""
                if "REGISTER" in cseq.upper():
                    self.log("UNREGISTER 200 OK")
                    self.registered.clear()
                    return
        self.log("UNREGISTER timeout (WVP 将按心跳超时离线)")

    def send_message(self, xml: str) -> None:
        body = xml.strip().replace("\n", "\r\n").encode("utf-8")
        if not body.endswith(b"\r\n"):
            body += b"\r\n"
        uri = f"sip:{self.domain}@{self.server_ip}:{self.server_port}"
        extra = ["Content-Type: Application/MANSCDP+xml"]
        headers = self.common_headers("MESSAGE", uri, f"sip:{self.domain}@{self.domain}", extra)
        self.send_sip(headers, body)

    def keepalive_loop(self) -> None:
        while not self.quit.wait(self.keepalive_sec):
            xml = f"""<?xml version="1.0"?>
<Notify>
<CmdType>Keepalive</CmdType>
<SN>{self.sn}</SN>
<DeviceID>{self.device_id}</DeviceID>
<Status>OK</Status>
</Notify>"""
            self.sn += 1
            self.send_message(xml)
            self.log("Keepalive sent")

    def echo_via(self, via: str) -> str:
        """回抄请求的 Via，并按 RFC 3581 给 rport 填上端口值。

        WVP 发请求时 Via 带的是无值的 `;rport` 标志。原样抄回去，WVP 的
        jain-sip 解析应答时会对空值 rport 抛 NullPointerException，整条应答
        被丢弃：INVITE 拿不到 200 OK，60s 后按「-1024 消息超时未回复」把流拆掉，
        画面就冻在最后一帧；MESSAGE 同理，目录同步也会超时。
        """
        if re.search(r"(?i);\s*rport\s*=", via):
            return via
        return re.sub(r"(?i);\s*rport(?=\s*;|\s*$)", f";rport={self.server_port}", via)

    def reply(self, req: str, code: str, reason: str, extra: list[str] | None = None, body: bytes = b"") -> None:
        vias = [self.echo_via(v) for v in sip_headers(req, "Via")]
        from_h = sip_header(req, "From")
        to_h = sip_header(req, "To")
        # 只有终结性应答才补 To tag。100 Trying 按 RFC 3261 不建立对话，带上 tag
        # 会让 WVP 的 jain-sip 去建 early dialog 并抛 NullPointerException，
        # 整条 INVITE 事务作废：WVP 收不到应答，60s 后按「消息超时未回复」拆掉流。
        if not code.startswith("1") and "tag=" not in to_h.lower():
            to_h = f"{to_h};tag={self.tag}"
        lines = [f"SIP/2.0 {code} {reason}"]
        for v in vias:
            lines.append(f"Via: {v}")
        lines.extend(
            [
                f"From: {from_h}",
                f"To: {to_h}",
                f"Call-ID: {sip_header(req, 'Call-ID')}",
                f"CSeq: {sip_header(req, 'CSeq')}",
                f"Contact: {self.contact()}",
                "User-Agent: easySVA-gb-sim",
            ]
        )
        if extra:
            lines.extend(extra)
        self.send_sip(lines, body)

    def catalog_xml(self, sn: str) -> str:
        return f"""<?xml version="1.0"?>
<Response>
<CmdType>Catalog</CmdType>
<SN>{sn}</SN>
<DeviceID>{self.device_id}</DeviceID>
<SumNum>1</SumNum>
<DeviceList Num="1">
<Item>
<DeviceID>{self.channel_id}</DeviceID>
<Name>demo-ipc</Name>
<Manufacturer>Demo</Manufacturer>
<Model>IPC</Model>
<Owner>Owner</Owner>
<CivilCode>34020000</CivilCode>
<Address>demo</Address>
<Parental>0</Parental>
<ParentID>{self.device_id}</ParentID>
<SafetyWay>0</SafetyWay>
<RegisterWay>1</RegisterWay>
<Secrecy>0</Secrecy>
<Status>ON</Status>
</Item>
</DeviceList>
</Response>"""

    def handle_message(self, req: str) -> None:
        body = req.split("\r\n\r\n", 1)[-1] if "\r\n\r\n" in req else ""
        cmd = xml_tag(body, "CmdType")
        sn = xml_tag(body, "SN") or str(self.sn)
        low = cmd.lower()
        xml = ""
        if low == "catalog":
            xml = self.catalog_xml(sn)
        elif low == "deviceinfo":
            xml = f"""<?xml version="1.0"?>
<Response>
<CmdType>DeviceInfo</CmdType>
<SN>{sn}</SN>
<DeviceID>{self.device_id}</DeviceID>
<DeviceName>demo-ipc</DeviceName>
<Result>OK</Result>
<Manufacturer>Demo</Manufacturer>
<Model>IPC</Model>
<Firmware>1.0</Firmware>
<Channel>1</Channel>
</Response>"""
        elif low == "devicestatus":
            xml = f"""<?xml version="1.0"?>
<Response>
<CmdType>DeviceStatus</CmdType>
<SN>{sn}</SN>
<DeviceID>{self.device_id}</DeviceID>
<Result>OK</Result>
<Online>ONLINE</Online>
<Status>OK</Status>
</Response>"""
        xml_bytes = b""
        extra: list[str] | None = None
        if xml:
            packed = xml.strip().replace("\n", "\r\n").encode("utf-8")
            if not packed.endswith(b"\r\n"):
                packed += b"\r\n"
            xml_bytes = packed
            extra = ["Content-Type: Application/MANSCDP+xml"]
        self.reply(req, "200", "OK", extra=extra, body=xml_bytes)
        if xml:
            self.send_message(xml)
            self.log(f"{cmd or 'MESSAGE'} SN={sn}")
        else:
            self.log(f"MESSAGE CmdType={cmd or '?'}")

    def parse_invite_media(self, sdp: str) -> tuple[str, int, str]:
        ip = self.server_ip
        cm = re.search(r"(?m)^c=IN IP4 ([0-9.]+)", sdp)
        if cm:
            ip = cm.group(1)
        port = 0
        mm = re.search(r"(?m)^m=video (\d+)", sdp)
        if mm:
            port = int(mm.group(1))
        y = ""
        ym = re.search(r"(?m)^y=(\d+)", sdp)
        if ym:
            y = ym.group(1)
        return ip, port, y

    def stop_media(self) -> None:
        self.media_stop.set()
        proc = self.ffmpeg
        if proc is not None and proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                proc.kill()
                try:
                    proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    pass
        elif proc is not None:
            try:
                proc.wait(timeout=1)
            except subprocess.TimeoutExpired:
                pass
        self.ffmpeg = None
        if self.pump and self.pump.is_alive():
            self.pump.join(timeout=2)
        self.pump = None
        self.media_stop.clear()

    def pump_mpegps(self, proc: subprocess.Popen, dest: tuple[str, int], ssrc: int) -> None:
        """ffmpeg -f vob 字节流连续塞进 RTP。不要按 pack 切/打 marker：ZLM 会只解出第一段 GOP 然后冻帧。"""
        assert proc.stdout is not None
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        # connect 而不是 sendto：ZLM 无人观看 60s 会关掉收流端口，之后内核回 ICMP
        # port unreachable。只有 connected UDP 才会把它变成 ECONNREFUSED，
        # 否则模拟器会朝着已关闭的端口一直空推，日志看着正常、画面却是死的。
        sock.connect(dest)
        seq = random.randint(0, 0xFFFF)
        sent = 0
        refused = 0
        refused_since = 0.0
        # 必须用单调时钟：WSL 的墙上时钟会不时倒退一两秒（实测 90s 内两次 -1.3s），
        # 用 time.time() 算出来的 RTP 时间戳会跟着倒退，ZLM 会报
        # 「Stamp expired is abnormal」并把这条流的时间轴判废。
        t0 = time.monotonic()
        last_log = t0
        try:
            while not self.media_stop.is_set() and proc.poll() is None:
                chunk = proc.stdout.read(1400)
                if not chunk:
                    break
                ts = int((time.monotonic() - t0) * 90000) & 0xFFFFFFFF
                header = struct.pack(
                    "!HHII",
                    (0x80 << 8) | 96,
                    seq & 0xFFFF,
                    ts,
                    ssrc & 0xFFFFFFFF,
                )
                seq = (seq + 1) & 0xFFFF
                try:
                    sock.send(header + chunk)
                except ConnectionRefusedError:
                    now = time.monotonic()
                    if now - refused_since > RTP_REFUSED_WINDOW_S:
                        refused, refused_since = 0, now
                    refused += 1
                    if refused >= RTP_REFUSED_LIMIT:
                        self.log(f"收流端口 {dest} 已关闭，停止推流，等待下一次 INVITE")
                        # 不留着 ffmpeg 堵在满管道上，下一次 INVITE 会重新拉起
                        proc.terminate()
                        break
                    continue
                sent += 1
                now = time.monotonic()
                if now - last_log >= 2:
                    self.log(f"vob RTP pkts={sent} dest={dest}")
                    last_log = now
        except Exception as exc:
            self.log(f"rtp pump error: {exc}")
        finally:
            self.log(f"vob RTP stop pkts={sent} ffmpeg={proc.poll()}")
            sock.close()

    def start_media(self, ip: str, port: int, ssrc: str) -> None:
        self.stop_media()
        if not os.path.isfile(self.video):
            raise FileNotFoundError(self.video)
        ssrc_i = int(ssrc) if ssrc.isdigit() else random.randint(1, 0x7FFFFFFF)
        if self.mux == "rtp_mpegts":
            dest = f"rtp://{ip}:{port}"
            cmd = [
                "ffmpeg",
                "-hide_banner",
                "-loglevel",
                "error",
                "-re",
                "-stream_loop",
                "-1",
                "-i",
                self.video,
                "-an",
                "-vf",
                video_filter(),
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-tune",
                "zerolatency",
                "-pix_fmt",
                "yuv420p",
                "-g",
                "25",
                "-ssrc",
                str(ssrc_i),
                "-f",
                "rtp_mpegts",
                dest,
            ]
            self.log("ffmpeg rtp_mpegts → " + dest)
            self.ffmpeg = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return
        cmd = [
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "error",
            "-re",
            "-stream_loop",
            "-1",
            "-i",
            self.video,
            "-an",
            "-vf",
            video_filter(),
            "-c:v",
            "libx264",
            "-preset",
            "veryfast",
            "-tune",
            "zerolatency",
            "-pix_fmt",
            "yuv420p",
            "-profile:v",
            "baseline",
            "-bf",
            "0",
            "-g",
            "25",
            "-f",
            "vob",
            "pipe:1",
        ]
        self.log(f"ffmpeg vob/RTP → {ip}:{port} ssrc={ssrc_i}")
        err_path = "/opt/SVA/wvp/gb_ffmpeg.err"
        try:
            os.makedirs("/opt/SVA/wvp", exist_ok=True)
            err_f = open(err_path, "ab")
        except OSError:
            err_f = subprocess.DEVNULL
        self.ffmpeg = subprocess.Popen(
            cmd, stdout=subprocess.PIPE, stderr=err_f, bufsize=0
        )
        self.pump = threading.Thread(
            target=self.pump_mpegps,
            args=(self.ffmpeg, (ip, port), ssrc_i),
            daemon=True,
        )
        self.pump.start()

    def handle_invite(self, req: str) -> None:
        self.reply(req, "100", "Trying")
        sdp_in = req.split("\r\n\r\n", 1)[-1] if "\r\n\r\n" in req else ""
        ip, port, ssrc = self.parse_invite_media(sdp_in)
        if port <= 0:
            self.reply(req, "488", "Not Acceptable Here")
            self.log("INVITE missing m=video port")
            return
        local_media = random.randint(20000, 30000)
        yline = ssrc or str(random.randint(1000000000, 1999999999))
        sdp_out = (
            "v=0\r\n"
            f"o={self.device_id} 0 0 IN IP4 {self.local_ip}\r\n"
            "s=Play\r\n"
            f"c=IN IP4 {self.local_ip}\r\n"
            "t=0 0\r\n"
            f"m=video {local_media} RTP/AVP 96\r\n"
            "a=sendonly\r\n"
            "a=rtpmap:96 PS/90000\r\n"
            f"y={yline}\r\n"
        )
        self.reply(req, "200", "OK", extra=["Content-Type: application/sdp"], body=sdp_out.encode("utf-8"))
        self.start_media(ip, port, yline)
        self.log(f"INVITE 200 OK → RTP {ip}:{port} ssrc={yline}")

    def handle_bye(self, req: str) -> None:
        self.stop_media()
        self.reply(req, "200", "OK")
        self.log("BYE, media stopped")

    def serve(self) -> None:
        uri = f"sip:{self.server_ip}:{self.server_port}"
        self.send_register()
        self.log(f"REGISTER → {self.server_ip}:{self.server_port} from {self.local_ip}:{self.local_port}")
        while not self.quit.is_set():
            try:
                data, addr = self.sock.recvfrom(65535)
            except socket.timeout:
                continue
            text = data.decode("utf-8", "replace")
            start = text.splitlines()[0] if text else ""
            if text.startswith("SIP/2.0 401") or text.startswith("SIP/2.0 407"):
                chal = sip_header(text, "WWW-Authenticate") or sip_header(text, "Proxy-Authenticate")
                auth = self.digest_auth(chal, "REGISTER", uri)
                self.send_register(auth)
                self.log(f"REGISTER with Digest (from {addr[0]}:{addr[1]})")
            elif text.startswith("SIP/2.0 200"):
                cseq = sip_header(text, "CSeq")
                if "REGISTER" in cseq.upper() and not self.registered.is_set():
                    self.registered.set()
                    self.log(f"REGISTER 200 OK device={self.device_id}")
                    threading.Thread(target=self.keepalive_loop, daemon=True).start()
            elif text.startswith("SIP/2.0 403"):
                self.log("REGISTER 403 forbidden (password/realm)")
            elif text.startswith("INVITE "):
                self.handle_invite(text)
            elif text.startswith("BYE "):
                self.handle_bye(text)
            elif text.startswith("ACK "):
                self.log("ACK")
            elif text.startswith("MESSAGE "):
                self.handle_message(text)
            elif text.startswith("INFO ") or text.startswith("OPTIONS ") or text.startswith("SUBSCRIBE "):
                self.reply(text, "200", "OK")
            elif text.startswith("SIP/2.0"):
                if "200" not in start:
                    self.log(f"SIP reply {start}")
            else:
                self.log(f"ignore {start}")

    def close(self) -> None:
        if self.closed:
            return
        self.closed = True
        self.quit.set()
        try:
            self.unregister()
        except Exception as ex:
            self.log(f"unregister error: {ex}")
        self.stop_media()
        try:
            self.sock.close()
        except OSError:
            pass


def video_filter() -> str:
    """横屏 pad，底边加一条来回走的指示条。

    静帧片源（水杯、大树）光看画面分不清「流断了」和「片源本身不动」，
    指示条一停就说明流真的停了。答辩要干净画面时 MOTION_BAR=0 关掉。
    真机不走模拟器，这里只影响演示片源。
    """
    chain = [
        "scale=1280:720:force_original_aspect_ratio=decrease",
        "pad=1280:720:(ow-iw)/2:(oh-ih)/2",
    ]
    if os.environ.get("MOTION_BAR", "1") != "0":
        chain.append(
            "drawbox=x='(iw-180)*(0.5-0.5*cos(2*PI*t/6))':y='ih-20'"
            ":w=180:h=14:color=lime@0.9:t=fill"
        )
    return ",".join(chain)


def guess_lan_ip() -> str:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        ip = sock.getsockname()[0]
        if ip and not ip.startswith("127."):
            return ip
    except OSError:
        pass
    finally:
        sock.close()
    return "127.0.0.1"


def parse_args() -> argparse.Namespace:
    lan = guess_lan_ip()
    p = argparse.ArgumentParser(description="easySVA GB28181 device simulator")
    p.add_argument("--server-ip", default=lan)
    p.add_argument("--server-port", type=int, default=5060)
    p.add_argument("--local-ip", default=lan)
    p.add_argument("--local-port", type=int, default=15060)
    p.add_argument("--device-id", default="34020000001320000001")
    p.add_argument("--channel-id", default="34020000001320000001")
    p.add_argument("--domain", default="3402000000")
    p.add_argument("--password", default="12345678")
    p.add_argument(
        "--video",
        default="/opt/easySVA-lib/opencv/doc/js_tutorials/js_assets/cup.mp4",
    )
    p.add_argument("--expires", type=int, default=3600)
    p.add_argument("--keepalive", type=int, default=20)
    p.add_argument("--mux", choices=("ps", "rtp_mpegts"), default="ps")
    return p.parse_args()


def main() -> int:
    args = parse_args()
    sim = Gb28181Sim(args)

    def _term(_signum, _frame):
        sim.log("stop")
        sim.quit.set()

    signal.signal(signal.SIGTERM, _term)
    signal.signal(signal.SIGINT, _term)
    try:
        sim.serve()
    finally:
        sim.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
