#!/usr/bin/env bash
# 在 ZLM 上开一个国标 RTP 收流通道（GB28181 PS → app=rtp）
# 用法：bash scripts/open_gb_rtp.sh [stream_id] [tcp=0|1]
# 返回 port 后，国标 IPC/模拟器向 本机IP:port 推 PS；再在网页点「同步国标设备」。
set -euo pipefail
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi

STREAM_ID="${1:-gbcam001}"
TCP_MODE="${2:-0}"
SECRET=$(grep -E '^secret=' /opt/SVA/mediaServer/config.ini | head -1 | cut -d= -f2- | tr -d '\r\n ')
API="http://127.0.0.1:9992/index/api/openRtpServer"

echo "openRtpServer stream_id=$STREAM_ID tcp_mode=$TCP_MODE"
# ZLM 参数名为 tcp_mode（不是 enable_tcp）
curl -sS -G "$API" \
  --data-urlencode "secret=${SECRET}" \
  --data-urlencode "port=0" \
  --data-urlencode "tcp_mode=${TCP_MODE}" \
  --data-urlencode "stream_id=${STREAM_ID}"
echo
echo "然后：设备管理 → 同步国标设备；预览流 app=rtp / $STREAM_ID"
