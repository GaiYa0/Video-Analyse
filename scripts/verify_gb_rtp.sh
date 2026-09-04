#!/usr/bin/env bash
# P3 媒体半程自证：openRtpServer → listRtpServer →（可选）同步 API
# 有国标模拟器推 PS 后，getMediaList 应出现 app=rtp。
set -euo pipefail
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi

STREAM_ID="${1:-gbcam001}"
SECRET=$(grep -E '^secret=' /opt/SVA/mediaServer/config.ini | head -1 | cut -d= -f2- | tr -d '\r\n ')
API_BASE="http://127.0.0.1:9992/index/api"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== [1] openRtpServer $STREAM_ID ==="
bash "$SCRIPT_DIR/open_gb_rtp.sh" "$STREAM_ID"
echo

echo "=== [2] listRtpServer ==="
curl -sS -G "$API_BASE/listRtpServer" --data-urlencode "secret=$SECRET"
echo

echo "=== [3] getMediaList (filter rtp) ==="
MEDIA_JSON=$(curl -sS -G "$API_BASE/getMediaList" --data-urlencode "secret=$SECRET" || true)
python3 -c "import json,sys; d=json.loads(sys.argv[1] or '{}'); rows=[(x.get('app'),x.get('stream'),x.get('schema')) for x in (d.get('data') or []) if x.get('app')=='rtp']; print(rows if rows else '(empty — 向 openRtp 返回的 port 推 GB28181 PS 后才会有画面)')" "$MEDIA_JSON"

echo
echo "=== [4] syncGb28181 (backend) ==="
API=http://127.0.0.1:9114
TOKEN=$(curl -sS -X POST "$API/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin).get("token",""))')
if [[ -n "$TOKEN" ]]; then
  curl -sS -X POST "$API/waring/device/syncGb28181" -H "Authorization: Bearer $TOKEN"
  echo
else
  echo "login failed (backend down?)"
fi

echo
echo "预览 URL: ws://127.0.0.1:9992/rtp/${STREAM_ID}.live.flv"
echo "无推流时黑屏是预期；推流成功后 getMediaList 非空再预览。"
