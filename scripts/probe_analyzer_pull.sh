#!/usr/bin/env bash
# Analyzer 拉流探测：对比 live/{apeId} 与 rtp/{stream} 能否被 ffprobe 打开。
# 不替代 A 的 verify_gb_rtp.sh（那份只管 openRtp / 同步，不测 Analyzer 打开的 RTSP）。
# 本机 WSL24 无 WVP 时，rtp 无媒体会失败，脚本必须把这句话打印清楚。
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi
set -u

LIVE_APE="cam918429"
RTP_STREAM="34020000001320000001_34020000001320000001"
ZLM_RTSP="rtsp://127.0.0.1:9994"
ANALYZER="http://127.0.0.1:9993"

usage() {
  cat <<'EOF'
用法:
  bash scripts/probe_analyzer_pull.sh
  bash scripts/probe_analyzer_pull.sh --live cam918429 --rtp 34020000001320000001_34020000001320000001

环境变量可覆盖：LIVE_APE RTP_STREAM ZLM_RTSP ANALYZER
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --live)
      LIVE_APE="${2:-}"
      shift 2
      ;;
    --rtp)
      RTP_STREAM="${2:-}"
      shift 2
      ;;
    --rtsp)
      ZLM_RTSP="${2:-}"
      shift 2
      ;;
    --analyzer)
      ANALYZER="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown arg: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if ! command -v ffprobe >/dev/null 2>&1; then
  echo "缺少 ffprobe。WSL: apt install ffmpeg 或用 /usr/local/ffmpeg/bin/ffprobe"
  exit 1
fi

probe_url() {
  local label="$1"
  local url="$2"
  echo "=== ffprobe ${label} ==="
  echo "url=${url}"
  if ffprobe -v error -rtsp_transport tcp -timeout 5000000 \
    -show_entries stream=codec_type,codec_name,width,height \
    -of default=noprint_wrappers=1 "${url}"; then
    echo "RESULT ${label}: OPEN"
  else
    echo "RESULT ${label}: NO_MEDIA_OR_TIMEOUT"
    if [[ "${label}" == rtp ]]; then
      echo "说明：无 WVP / 未 INVITE / 未推 PS 时 rtp 打不开是预期。ZLM 有媒体但这里失败 → 给同学 B 排 Analyzer 取流。"
    else
      echo "说明：直连 live 无代理时失败是预期。先对设备点「启用监控」或推 rtmp://127.0.0.1:9995/live/webcam。"
    fi
  fi
  echo
}

echo "=== Analyzer health ${ANALYZER} ==="
if curl -fsS -m 3 "${ANALYZER}/" >/dev/null; then
  echo "RESULT analyzer: UP"
  curl -fsS -m 3 "${ANALYZER}/" || true
  echo
else
  echo "RESULT analyzer: DOWN（本机先按 architecture-analyzer.md 第 10 节拉起 easysva-analyzer）"
  echo
fi

probe_url live "${ZLM_RTSP}/live/${LIVE_APE}"
probe_url rtp "${ZLM_RTSP}/rtp/${RTP_STREAM}"

echo "对照：Analyzer 直连应拉 live/{apeId}，国标应拉 rtp/{设备_通道}，不要把 ws FLV 塞给 Analyzer。"
echo "媒体半程（openRtp/同步）仍用 scripts/verify_gb_rtp.sh。"
