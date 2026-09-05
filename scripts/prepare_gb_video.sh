#!/usr/bin/env bash
# 把任意片源转成国标模拟器更稳的 H.264 横屏（不提交成品，fixtures/*.mp4 已 gitignore）
# 用法：bash scripts/prepare_gb_video.sh 输入.mp4 [输出.mp4]
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi
set -euo pipefail
IN="${1:-}"
if [[ -z "$IN" || ! -f "$IN" ]]; then
  echo "usage: $0 <input.mp4> [output.mp4]"
  exit 1
fi
OUT="${2:-}"
if [[ -z "$OUT" ]]; then
  base="${IN%.*}"
  OUT="${base}-h264.mp4"
fi
ffmpeg -y -i "$IN" \
  -vf "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2" \
  -c:v libx264 -pix_fmt yuv420p -preset veryfast -profile:v baseline -bf 0 -g 25 -an \
  "$OUT"
echo "wrote $OUT"
ffprobe -hide_banner -select_streams v:0 -show_entries stream=codec_name,width,height -of csv=p=0 "$OUT"
