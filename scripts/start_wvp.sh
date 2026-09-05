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
    if section == 'media' and (s.startswith('stream-ip:') or s.startswith('sdp-ip:')):
        line = line.split(':', 1)[0] + ': ' + lan
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

pkill -f 'wvp-pro-' || true
sleep 1
cd /opt/SVA/wvp
nohup java -jar "$JAR" \
  --spring.profiles.active=easysva \
  --spring.config.additional-location=optional:file:/opt/SVA/wvp/config/ \
  > /opt/SVA/wvp/wvp.log 2>&1 &
echo $! > /opt/SVA/wvp/wvp.pid
echo "WVP pid=$(cat /opt/SVA/wvp/wvp.pid)"

# ZLM hook → WVP
INI=/opt/SVA/mediaServer/config.ini
if [[ -f "$INI" ]]; then
  [[ -f "$INI.bak.pre-wvp" ]] || cp "$INI" "$INI.bak.pre-wvp"
  python3 - <<'PY'
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen

p = Path("/opt/SVA/mediaServer/config.ini")
lines = p.read_text(encoding="utf-8", errors="ignore").splitlines()
out=[]
section=None
hooks={
  "enable":"enable=1",
  "timeoutsec":"timeoutSec=30",
  "on_server_started":"on_server_started=http://127.0.0.1:18080/index/hook/on_server_started",
  "on_stream_changed":"on_stream_changed=http://127.0.0.1:18080/index/hook/on_stream_changed",
  "on_stream_none_reader":"on_stream_none_reader=http://127.0.0.1:18080/index/hook/on_stream_none_reader",
  "on_stream_not_found":"on_stream_not_found=http://127.0.0.1:18080/index/hook/on_stream_not_found",
  "on_play":"on_play=http://127.0.0.1:18080/index/hook/on_play",
  "on_publish":"on_publish=http://127.0.0.1:18080/index/hook/on_publish",
}
general={
  "maxstreamwaitms":"maxStreamWaitMS=25000",
  "streamnonereaderdelayms":"streamNoneReaderDelayMS=60000",
}
secret=""
for line in lines:
    s=line.strip()
    if s.startswith("[") and s.endswith("]"):
        section=s.lower()
        out.append(line)
        continue
    if "=" in s:
        key=s.split("=",1)[0].strip().lower()
        if section=="[hook]" and key in hooks:
            out.append(hooks[key]); continue
        if section=="[general]" and key in general:
            out.append(general[key]); continue
        if section=="[api]" and key=="secret":
            secret=s.split("=",1)[1].strip()
    out.append(line)
p.write_text("\n".join(out)+"\n", encoding="utf-8")
print("ZLM hook -> WVP :18080, maxStreamWaitMS=25000, streamNoneReaderDelayMS=60000")
if secret:
    qs=urlencode({
        "secret": secret,
        "hook.enable": "1",
        "hook.timeoutSec": "30",
        "hook.on_stream_not_found": "http://127.0.0.1:18080/index/hook/on_stream_not_found",
        "hook.on_play": "http://127.0.0.1:18080/index/hook/on_play",
        "hook.on_publish": "http://127.0.0.1:18080/index/hook/on_publish",
        "hook.on_stream_changed": "http://127.0.0.1:18080/index/hook/on_stream_changed",
        "hook.on_stream_none_reader": "http://127.0.0.1:18080/index/hook/on_stream_none_reader",
        "general.maxStreamWaitMS": "25000",
        "general.streamNoneReaderDelayMS": "60000",
    })
    try:
        with urlopen("http://127.0.0.1:9992/index/api/setServerConfig?"+qs, timeout=5) as r:
            print("setServerConfig", r.read()[:200].decode("utf-8","replace"))
    except Exception as e:
        print("setServerConfig skip:", e)
PY
fi

for i in $(seq 1 40); do
  if ss -tlnp 2>/dev/null | grep -q ':18080'; then
    echo "WVP HTTP :18080 OK；SIP 见下行"
    ss -tulnp 2>/dev/null | grep -E '5060|18080' || true
    echo "管理页 http://127.0.0.1:18080/  账号 admin / SvaDemo@2026"
    exit 0
  fi
  sleep 2
done
tail -50 /opt/SVA/wvp/wvp.log
exit 1
