#!/usr/bin/env bash
# 国标模拟器：SIP REGISTER → WVP 点播 → ZLM app=rtp
# 用法：bash scripts/start_gb_sim.sh
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi
set -euo pipefail
export PATH=/usr/bin:/bin:/usr/local/bin

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SIM_PY="$SCRIPT_DIR/gb28181_sim.py"
sed -i 's/\r$//' "$SIM_PY" 2>/dev/null || true

LAN_IP="${LAN_IP:-$(hostname -I | awk '{print $1}')}"
DEVICE_ID="${DEVICE_ID:-34020000001320000001}"
CHANNEL_ID="${CHANNEL_ID:-$DEVICE_ID}"
WVP="${WVP_BASE:-http://127.0.0.1:18080}"
VIDEO="${VIDEO:-/opt/easySVA-lib/opencv/doc/js_tutorials/js_assets/cup.mp4}"
SECRET=$(grep -E '^secret=' /opt/SVA/mediaServer/config.ini | head -1 | cut -d= -f2- | tr -d '\r\n ')

command -v ffmpeg >/dev/null || { echo "需要 ffmpeg"; exit 1; }
[[ -f "$VIDEO" ]] || { echo "缺少测试视频 $VIDEO"; exit 1; }
ss -tulnp | grep -q ':5060' || { echo "WVP SIP 5060 未监听，先 bash scripts/start_wvp.sh"; exit 1; }

pkill -f 'gb28181_sim.py' 2>/dev/null || true
sleep 1

mkdir -p /opt/SVA/wvp
: > /opt/SVA/wvp/gb28181_sim.log
nohup python3 "$SIM_PY" \
  --server-ip "$LAN_IP" \
  --local-ip "$LAN_IP" \
  --device-id "$DEVICE_ID" \
  --channel-id "$CHANNEL_ID" \
  --video "$VIDEO" \
  > /opt/SVA/wvp/gb28181_sim.log 2>&1 &
echo $! > /opt/SVA/wvp/gb28181_sim.pid
echo "sim pid=$(cat /opt/SVA/wvp/gb28181_sim.pid)  log=/opt/SVA/wvp/gb28181_sim.log  sip=${LAN_IP}:15060 → ${LAN_IP}:5060"

online=0
for i in $(seq 1 25); do
  if grep -q 'REGISTER 200 OK' /opt/SVA/wvp/gb28181_sim.log 2>/dev/null; then
    online=1
    break
  fi
  if grep -q 'REGISTER 403' /opt/SVA/wvp/gb28181_sim.log 2>/dev/null; then
    break
  fi
  sleep 1
done
echo "=== sim log ==="
cat /opt/SVA/wvp/gb28181_sim.log
[[ "$online" = 1 ]] || { echo "REGISTER 失败"; exit 1; }

echo "=== 纠正 WVP 设备 sdp_ip（本机模拟器用 127.0.0.1，避免 DHCP 换 IP 后 INVITE 发错地址）==="
mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ -e \
  "UPDATE wvp.wvp_device SET sdp_ip='127.0.0.1' WHERE device_id='$DEVICE_ID';" || true
python3 - <<PY
import json, subprocess
field = "$DEVICE_ID"
raw = subprocess.check_output(["redis-cli", "-n", "7", "hget", "VMP_DEVICE_INFO", field], text=True)
if not raw.strip():
    raise SystemExit(0)
d = json.loads(raw)
d["sdpIp"] = "127.0.0.1"
subprocess.check_call(["redis-cli", "-n", "7", "hset", "VMP_DEVICE_INFO", field, json.dumps(d, separators=(",", ":"))])
print("redis sdpIp -> 127.0.0.1")
PY

echo "=== WVP 设备（host 应为 :15060，不是 :5060）==="
mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ -N -e \
  "SELECT device_id, on_line, host_address, stream_mode FROM wvp.wvp_device WHERE device_id='$DEVICE_ID';" || true

wvp_login() {
  local md5 token
  md5=$(printf '%s' 'SvaDemo@2026' | md5sum | awk '{print $1}')
  token=$(curl --noproxy 127.0.0.1 -sS -m 8 \
    "$WVP/api/user/login?username=admin&password=${md5}" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin).get("data",{}).get("accessToken",""))')
  printf '%s' "$token"
}

TOKEN=$(wvp_login)
[[ -n "$TOKEN" ]] || { echo "WVP 登录失败"; exit 1; }
echo "WVP token=${TOKEN:0:8}..."
AUTH=(-H "access-token: $TOKEN")

echo "=== 切 UDP 并同步目录 ==="
curl --noproxy 127.0.0.1 -sS -m 8 "${AUTH[@]}" -X POST \
  "$WVP/api/device/query/transport/${DEVICE_ID}/UDP" || true
echo
curl --noproxy 127.0.0.1 -sS -m 15 "${AUTH[@]}" \
  "$WVP/api/device/query/devices/${DEVICE_ID}/sync" || true
echo

have_ch=0
for i in $(seq 1 20); do
  CH=$(mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ -N -e \
    "SELECT COALESCE(NULLIF(gb_device_id,''), device_id) FROM wvp.wvp_device_channel WHERE data_device_id=(SELECT id FROM wvp.wvp_device WHERE device_id='$DEVICE_ID' LIMIT 1) LIMIT 1;" 2>/dev/null || true)
  CH=$(printf '%s' "$CH" | tr -d '\r\n ')
  if [[ -n "$CH" && "$CH" != "NULL" ]]; then
    CHANNEL_ID="$CH"
    have_ch=1
    break
  fi
  sleep 1
done
echo "channel=${CHANNEL_ID} found=${have_ch}"
if [[ "$have_ch" != 1 ]]; then
  echo "尚无通道，点播可能失败。sim/WVP 日志："
  tail -20 /opt/SVA/wvp/gb28181_sim.log
  grep -E 'Catalog|通道|注册' /opt/SVA/wvp/wvp.log | tail -20 || true
fi

echo "=== WVP 点播 ==="
curl --noproxy 127.0.0.1 -sS -m 15 "${AUTH[@]}" \
  "$WVP/api/play/stop/${DEVICE_ID}/${CHANNEL_ID}" >/dev/null || true
# 等上一轮 in-flight 点播结束，避免「已有请求在途」吃掉新 INVITE
sleep 2
PLAY=$(curl --noproxy 127.0.0.1 -sS -m 90 "${AUTH[@]}" \
  "$WVP/api/play/start/${DEVICE_ID}/${CHANNEL_ID}" || true)
echo "$PLAY" | python3 -c 'import sys; print(sys.stdin.read()[:500])'
echo
STREAM=$(echo "$PLAY" | python3 -c 'import sys,json,re
raw=sys.stdin.read()
try:
    d=json.loads(raw)
    print((d.get("data") or {}).get("stream") or "")
except Exception:
    print("")
' || true)
if [[ -z "$STREAM" ]]; then
  STREAM="${DEVICE_ID}_${CHANNEL_ID}"
fi

echo "=== ZLM getMediaList app=rtp ==="
ok=0
for i in $(seq 1 25); do
  MEDIA=$(curl --noproxy 127.0.0.1 -sS -m 5 \
    "http://127.0.0.1:9992/index/api/getMediaList?secret=${SECRET}" || true)
  HIT=$(python3 -c 'import json,sys
raw=sys.argv[1] if len(sys.argv)>1 else ""
try:
    d=json.loads(raw or "{}")
except Exception:
    d={}
rows=[]
for x in (d.get("data") or []):
    if x.get("app")=="rtp":
        rows.append("%s %s schema=%s v=%s"% (x.get("app"), x.get("stream"), x.get("schema") or x.get("schema"), x.get("video") or x.get("tracks") or ""))
print("\n".join(str(r) for r in rows))
' "$MEDIA")
  if [[ -n "$HIT" ]]; then
    echo "$HIT"
    ok=1
    break
  fi
  sleep 1
done
if [[ "$ok" != 1 ]]; then
  echo "(尚无 rtp 流)"
  echo "--- sim ---"; tail -30 /opt/SVA/wvp/gb28181_sim.log
  echo "--- wvp ---"; grep -E '点播|INVITE|rtp|超时' /opt/SVA/wvp/wvp.log | tail -30 || true
fi

echo "=== 同步业务库 ==="
API=http://127.0.0.1:9114
BT=$(curl --noproxy 127.0.0.1 -sS -X POST "$API/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin).get("token",""))' || true)
if [[ -n "${BT:-}" ]]; then
  curl --noproxy 127.0.0.1 -sS -X POST "$API/waring/device/syncGb28181" \
    -H "Authorization: Bearer $BT"
  echo
fi

echo
echo "预览: http://127.0.0.1:9992/rtp/${STREAM}.live.flv"
echo "      ws://127.0.0.1:9992/rtp/${STREAM}.live.flv"
echo "WVP:  http://127.0.0.1:18080/   admin / SvaDemo@2026"
echo "业务: http://localhost:8080/   → 同步国标设备"
echo "模拟器需保持运行（pid $(cat /opt/SVA/wvp/gb28181_sim.pid)）；下班用 stop_all.sh"
