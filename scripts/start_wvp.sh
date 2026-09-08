#!/usr/bin/env bash
# 启动 / 重启 WVP（SIP 5060）并写 ZLM hook
set -euo pipefail
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:/usr/bin:/bin"

WVP_HOME="${WVP_HOME:-/opt/wvp-GB28181-pro}"
JAR=$(ls -1 "$WVP_HOME"/target/wvp-pro-*.jar 2>/dev/null | grep -v original | head -1 || true)
[[ -n "$JAR" ]] || { echo "缺少 jar，先 bash scripts/build_wvp.sh"; exit 1; }

mkdir -p /opt/SVA/wvp/config
# 外部配置（jar 内可能不含 yml）
if [[ -f "$WVP_HOME/src/main/resources/application-easysva.yml" ]]; then
  cp "$WVP_HOME/src/main/resources/application.yml" /opt/SVA/wvp/config/ 2>/dev/null || true
  cp "$WVP_HOME/src/main/resources/application-easysva.yml" /opt/SVA/wvp/config/
fi
[[ -f /opt/SVA/wvp/config/application-easysva.yml ]] || {
  echo "缺少 /opt/SVA/wvp/config/application-easysva.yml，先 bash scripts/setup_wvp.sh"
  exit 1
}

LAN_IP="${LAN_IP:-$(hostname -I | awk '{print $1}')}"
python3 -c "
from pathlib import Path
lan = '''$LAN_IP'''
p = Path('/opt/SVA/wvp/config/application-easysva.yml')
section = None
out = []
for line in p.read_text(encoding='utf-8').splitlines():
    s = line.strip()
    if line and not line[0].isspace() and s.endswith(':') and not s.startswith('#'):
        section = s[:-1]
    if section == 'sip' and (s.startswith('ip:') or s.startswith('show-ip:')):
        line = line.split(':', 1)[0] + ': ' + lan
    if section == 'media' and s.startswith('sdp-ip:'):
        line = line.split(':', 1)[0] + ': ' + lan
    if section == 'media' and s.startswith('stream-ip:'):
        # 浏览器在演示机本机播；LAN:9992 不通（Clash / 未转发）
        line = line.split(':', 1)[0] + ': 127.0.0.1'
    out.append(line)
p.write_text('\\n'.join(out) + '\\n', encoding='utf-8')
print('WVP SIP 绑定 %s:5060' % lan)
"

mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ -e \
  "UPDATE wvp.wvp_device SET sdp_ip='${LAN_IP}' WHERE sdp_ip IS NULL OR (sdp_ip<>'${LAN_IP}' AND sdp_ip<>'127.0.0.1');" \
  2>/dev/null || true
python3 - <<PY
import json, subprocess
lan = "${LAN_IP}"
try:
    raw = subprocess.check_output(["redis-cli", "-n", "7", "hgetall", "VMP_DEVICE_INFO"], text=True)
except Exception:
    raise SystemExit(0)
lines = [x for x in raw.splitlines() if x]
n = 0
for i in range(0, len(lines) - 1, 2):
    field, val = lines[i], lines[i + 1]
    try:
        d = json.loads(val)
    except Exception:
        continue
    sip = str(d.get("sdpIp") or d.get("sdp_ip") or "")
    if sip and sip != lan and sip != "127.0.0.1":
        d["sdpIp"] = lan
        subprocess.check_call(
            ["redis-cli", "-n", "7", "hset", "VMP_DEVICE_INFO", field, json.dumps(d, separators=(",", ":"))]
        )
        n += 1
if n:
    print("redis sdpIp 已纠正 %d 台（跳过 127.0.0.1）" % n)
PY

pkill -f 'wvp-pro-' 2>/dev/null || true
sleep 1
# 确保旧进程释放 SIP/HTTP，避免「端口被占用」假启动
for i in $(seq 1 15); do
  left=$(pgrep -f 'wvp-pro-' || true)
  if [[ -z "$left" ]] && ! ss -tulnp 2>/dev/null | grep -qE ':5060|:18080'; then
    break
  fi
  pkill -9 -f 'wvp-pro-' 2>/dev/null || true
  sleep 1
done
if pgrep -f 'wvp-pro-' >/dev/null 2>&1 || ss -tulnp 2>/dev/null | grep -qE ':5060|:18080'; then
  echo "WARN: 旧 WVP/5060/18080 可能仍占用，继续尝试启动"
fi
cd /opt/SVA/wvp
nohup java -jar "$JAR" \
  --spring.profiles.active=easysva \
  --spring.config.additional-location=optional:file:/opt/SVA/wvp/config/ \
  > /opt/SVA/wvp/wvp.log 2>&1 &
echo $! > /opt/SVA/wvp/wvp.pid
echo "WVP pid=$(cat /opt/SVA/wvp/wvp.pid)"

WVP_READY=0
for i in $(seq 1 40); do
  if ss -tlnp 2>/dev/null | grep -q ':18080'; then
    WVP_READY=1
    echo "WVP HTTP :18080 OK；SIP 见下行"
    ss -tulnp 2>/dev/null | grep -E '5060|18080' || true
    break
  fi
  sleep 2
done
if [[ "$WVP_READY" -ne 1 ]]; then
  tail -50 /opt/SVA/wvp/wvp.log
  exit 1
fi

# ZLM hook → WVP（国标点播必需）；不要挂 on_publish/on_play
# 原因：WVP 启动时 setZLMConfig 会把 on_publish/on_play 写回 ZLM（要求 pushKey），
# 导致 live/* RTMP（工位/test1/摄像头）401。必须等 WVP 就绪后再清空并校验。
# 国标 rtp 点播只依赖 on_stream_not_found 等，不依赖 on_publish。
INI=/opt/SVA/mediaServer/config.ini
if [[ -f "$INI" ]]; then
  [[ -f "$INI.bak.pre-wvp" ]] || cp "$INI" "$INI.bak.pre-wvp"
  # 给 WVP 首次 setZLMConfig 一点时间，再覆盖
  sleep 3
  python3 - <<'PY'
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen
import json, time

p = Path("/opt/SVA/mediaServer/config.ini")
HOOK_WVP = {
    "on_server_started": "http://127.0.0.1:18080/index/hook/on_server_started",
    "on_stream_changed": "http://127.0.0.1:18080/index/hook/on_stream_changed",
    "on_stream_none_reader": "http://127.0.0.1:18080/index/hook/on_stream_none_reader",
    "on_stream_not_found": "http://127.0.0.1:18080/index/hook/on_stream_not_found",
}
CLEAR_KEYS = ("on_publish", "on_play")

def rewrite_ini():
    lines = p.read_text(encoding="utf-8", errors="ignore").splitlines()
    out = []
    section = None
    secret = ""
    hooks = {
        "enable": "enable=1",
        "timeoutsec": "timeoutSec=30",
        "on_server_started": "on_server_started=" + HOOK_WVP["on_server_started"],
        "on_stream_changed": "on_stream_changed=" + HOOK_WVP["on_stream_changed"],
        "on_stream_none_reader": "on_stream_none_reader=" + HOOK_WVP["on_stream_none_reader"],
        "on_stream_not_found": "on_stream_not_found=" + HOOK_WVP["on_stream_not_found"],
        "on_play": "on_play=",
        "on_publish": "on_publish=",
    }
    general = {
        "maxstreamwaitms": "maxStreamWaitMS=25000",
        "streamnonereaderdelayms": "streamNoneReaderDelayMS=60000",
    }
    seen_hook = set()
    for line in lines:
        s = line.strip()
        if s.startswith("[") and s.endswith("]"):
            section = s.lower()
            out.append(line)
            continue
        if "=" in s:
            key = s.split("=", 1)[0].strip().lower()
            if section == "[hook]" and key in hooks:
                out.append(hooks[key])
                seen_hook.add(key)
                continue
            if section == "[general]" and key in general:
                out.append(general[key])
                continue
            if section == "[api]" and key == "secret":
                secret = s.split("=", 1)[1].strip()
        out.append(line)
    missing = [hooks[k] for k in CLEAR_KEYS if k not in seen_hook]
    if missing and any(x.strip().lower() == "[hook]" for x in out):
        rebuilt, injected = [], False
        for line in out:
            rebuilt.append(line)
            if (not injected) and line.strip().lower() == "[hook]":
                rebuilt.extend(missing)
                injected = True
        out = rebuilt
    p.write_text("\n".join(out) + "\n", encoding="utf-8")
    return secret

def set_and_check(secret):
    qs = urlencode({
        "secret": secret,
        "hook.enable": "1",
        "hook.timeoutSec": "30",
        "hook.on_stream_not_found": HOOK_WVP["on_stream_not_found"],
        "hook.on_stream_changed": HOOK_WVP["on_stream_changed"],
        "hook.on_stream_none_reader": HOOK_WVP["on_stream_none_reader"],
        "hook.on_server_started": HOOK_WVP["on_server_started"],
        # 空串热更新：清掉 WVP setZLMConfig 写回的鉴权 hook
        "hook.on_play": "",
        "hook.on_publish": "",
        "general.maxStreamWaitMS": "25000",
        "general.streamNoneReaderDelayMS": "60000",
    })
    with urlopen("http://127.0.0.1:9992/index/api/setServerConfig?" + qs, timeout=5) as r:
        print("setServerConfig", r.read()[:200].decode("utf-8", "replace"))
    with urlopen(
        "http://127.0.0.1:9992/index/api/getServerConfig?secret=" + secret, timeout=5
    ) as r:
        data = (json.loads(r.read().decode("utf-8", "replace")).get("data") or [{}])[0]
    pub = str(data.get("hook.on_publish") or data.get("hook.on_publish".lower()) or "")
    play = str(data.get("hook.on_play") or "")
    # ZLM 返回键名可能带 hook. 前缀或不带
    for k, v in data.items():
        lk = str(k).lower().replace(".", "_")
        if lk.endswith("on_publish") or lk == "hook_on_publish":
            pub = str(v or "")
        if lk.endswith("on_play") or lk == "hook_on_play":
            play = str(v or "")
    ok = (not pub.strip()) and (not play.strip())
    print("verify on_publish=%r on_play=%r -> %s" % (pub, play, "OK" if ok else "NEED_RETRY"))
    return ok

secret = rewrite_ini()
if not secret:
    print("WARN: 未读到 ZLM secret，跳过 hook 热更新")
else:
    ok = False
    for attempt in range(1, 6):
        secret = rewrite_ini() or secret
        try:
            ok = set_and_check(secret)
        except Exception as e:
            print("setServerConfig attempt %d skip: %s" % (attempt, e))
            ok = False
        if ok:
            break
        # WVP 可能稍后再次 setZLMConfig，稍等再清
        time.sleep(2)
    if ok:
        print("ZLM hook -> WVP :18080 (not_found/none_reader/changed); on_publish/on_play CLEARED")
        print("maxStreamWaitMS=25000, streamNoneReaderDelayMS=60000")
    else:
        print("ERROR: 无法清空 ZLM on_publish/on_play（WVP 可能仍在写回），live RTMP 可能 401")
        raise SystemExit(2)
PY
fi

echo "管理页 http://127.0.0.1:18080/  账号 admin / SvaDemo@2026"
