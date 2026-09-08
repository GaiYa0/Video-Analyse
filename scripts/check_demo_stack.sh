#!/usr/bin/env bash
# 演示机开机 / 双源睡岗保栈自检（同学 A）
# 用法：
#   bash scripts/check_demo_stack.sh
#   bash scripts/check_demo_stack.sh --dual-sleep
#   bash scripts/check_demo_stack.sh --dual-sleep --live camXXXX --rtp 3402…_3402…
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi
set -u
export PATH=/usr/bin:/bin:/usr/local/bin

DUAL=0
LIVE_APE="${LIVE_APE:-}"
RTP_STREAM="${RTP_STREAM:-34020000001320000001_34020000001320000001}"
ZLM_HTTP="${ZLM_HTTP:-http://127.0.0.1:9992}"
ZLM_RTSP="${ZLM_RTSP:-rtsp://127.0.0.1:9994}"
BACKEND="${BACKEND:-http://127.0.0.1:9114}"
WVP="${WVP:-http://127.0.0.1:18080}"
WEB="${WEB:-http://127.0.0.1/}"
SECRET=""

usage() {
  cat <<'EOF'
用法:
  bash scripts/check_demo_stack.sh              # 业务栈 + WVP 端口/进程
  bash scripts/check_demo_stack.sh --dual-sleep # 再查 live + rtp 可被 ffprobe 打开

可选:
  --live <apeId>     直连/工位 live 流 ape_id（不传则只警告）
  --rtp  <stream>    国标 rtp 流名，默认 34020000001320000001_34020000001320000001
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dual-sleep) DUAL=1; shift ;;
    --live) LIVE_APE="${2:-}"; shift 2 ;;
    --rtp) RTP_STREAM="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -f /opt/SVA/mediaServer/config.ini ]]; then
  SECRET=$(grep -E '^secret=' /opt/SVA/mediaServer/config.ini | head -1 | cut -d= -f2- | tr -d '\r\n ')
fi

fail=0
pass() { echo "OK   $*"; }
warn() { echo "WARN $*"; }
bad()  { echo "FAIL $*"; fail=1; }

echo "=== 进程 ==="
pgrep -f 'java -jar backend.jar' >/dev/null && pass "backend.jar" || bad "backend.jar 未运行"
pgrep -x MediaServer >/dev/null && pass "MediaServer" || bad "MediaServer 未运行"
pgrep -x Analyzer >/dev/null && pass "Analyzer" || bad "Analyzer 未运行"
pgrep -f 'wvp-pro-' >/dev/null && pass "WVP" || bad "WVP 未运行（国标演示需要）"
if pgrep -f 'gb28181_sim.py' >/dev/null; then
  pass "gb28181_sim.py"
else
  warn "gb28181_sim.py 未运行（无国标画面时先 start_gb_sim / start_demo -WithGbSim）"
fi

echo "=== HTTP / 端口 ==="
code=$(curl --noproxy 127.0.0.1 -sS -m 5 -o /dev/null -w '%{http_code}' "$WEB" || echo 000)
[[ "$code" == "200" ]] && pass "web $WEB -> $code" || bad "web $WEB -> $code"
code=$(curl --noproxy 127.0.0.1 -sS -m 5 -o /dev/null -w '%{http_code}' "$BACKEND/" || echo 000)
# backend 根路径可能 200/302/401/404，只要端口通
if ss -tlnp 2>/dev/null | grep -q ':9114'; then
  pass "backend :9114 listening (http=$code)"
else
  bad "backend :9114 未监听"
fi
code=$(curl --noproxy 127.0.0.1 -sS -m 5 -o /dev/null -w '%{http_code}' "$WVP/" || echo 000)
[[ "$code" == "200" ]] && pass "WVP $WVP -> $code" || bad "WVP $WVP -> $code"
ss -tlnp 2>/dev/null | grep -q ':9992' && pass "ZLM HTTP :9992" || bad "ZLM HTTP :9992 未监听"
ss -tlnp 2>/dev/null | grep -qE ':5060' && pass "SIP :5060" || bad "SIP :5060 未监听"

echo "=== ZLM app=rtp（国标点播）==="
if [[ -n "$SECRET" ]]; then
  media=$(curl --noproxy 127.0.0.1 -sS -m 5 \
    "$ZLM_HTTP/index/api/getMediaList?secret=${SECRET}&app=rtp" || true)
  if echo "$media" | grep -q "$RTP_STREAM"; then
    pass "getMediaList 含 $RTP_STREAM"
  else
    bad "getMediaList 无 $RTP_STREAM（先点播 / 跑 start_gb_sim / 业务 warmRtp）"
    echo "  hint: 预览或布控会触发 warmRtp；也可 WVP 通道「播放」"
  fi
else
  warn "读不到 ZLM secret，跳过 getMediaList"
fi

if [[ "$DUAL" -eq 1 ]]; then
  echo "=== 双源 ffprobe（Analyzer 应拉的 URL）==="
  if ! command -v ffprobe >/dev/null 2>&1; then
    bad "缺少 ffprobe"
  else
    if [[ -n "$LIVE_APE" ]]; then
      url="${ZLM_RTSP}/live/${LIVE_APE}"
      echo "probe live $url"
      if ffprobe -v error -rtsp_transport tcp -timeout 5000000 \
        -show_entries stream=codec_name,width,height -of csv=p=0 "$url" >/tmp/demo_probe_live.txt 2>/dev/null; then
        pass "live OPEN  $(tr '\n' ' ' </tmp/demo_probe_live.txt)"
      else
        bad "live NO_MEDIA（工位未推流 / 未启用监控 / ape_id 不对）"
      fi
    else
      warn "未传 --live <apeId>，跳过工位 live 探测（双源睡岗请补上）"
    fi
    url="${ZLM_RTSP}/rtp/${RTP_STREAM}"
    echo "probe rtp $url"
    if ffprobe -v error -rtsp_transport tcp -timeout 5000000 \
      -show_entries stream=codec_name,width,height -of csv=p=0 "$url" >/tmp/demo_probe_rtp.txt 2>/dev/null; then
      pass "rtp OPEN  $(tr '\n' ' ' </tmp/demo_probe_rtp.txt)"
      echo "  Analyzer 国标布控应使用: $url"
    else
      bad "rtp NO_MEDIA（模拟器未推 / 未 INVITE）"
    fi
  fi
  echo "=== 双源睡岗联调提醒（同学 B 在网页操作）==="
  echo "1) 工位 RTSP/live 设备：布控 on_sleep_pose，录像引擎=算法服务器"
  echo "2) 国标 demo-ipc：布控 on_sleep_pose；确认 Analyzer 日志 streamUrl 为 rtsp://127.0.0.1:9994/rtp/..."
  echo "3) 告警列表应有证据图/视频可播（upload/alarm）"
fi

echo "=== 汇总 ==="
if [[ "$fail" -eq 0 ]]; then
  echo "STACK_OK"
  exit 0
fi
echo "STACK_FAIL"
exit 1
