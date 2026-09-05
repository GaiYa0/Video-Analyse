#!/usr/bin/env bash
# 把仓库 server/ 编进 /opt/SVA/server/Analyzer 并重启（改 AvPullStream 等之后用）
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi
set -euo pipefail
export PATH="/usr/local/cuda/bin:/usr/bin:/bin:${PATH:-}"
SRC="/mnt/e/video-analysis/Video-Analyse-main/server"
BUILD="$SRC/build"
mkdir -p "$BUILD"
cd "$BUILD"
cmake .. -DCMAKE_BUILD_TYPE=Release -DSVA_ONNXRUNTIME_GPU=ON
cmake --build . -j"$(nproc)"
[[ -x "$BUILD/Analyzer" ]] || { echo "build missing Analyzer"; exit 1; }
pkill -f 'Analyzer -f' || true
sleep 2
cp -a /opt/SVA/server/Analyzer "/opt/SVA/server/Analyzer.bak.$(date +%Y%m%d%H%M)" || true
cp -a "$BUILD/Analyzer" /opt/SVA/server/Analyzer
cd /opt/SVA/server
: > log.out
nohup ./Analyzer -f /opt/SVA/config.json > log.out 2>&1 &
echo "Analyzer pid=$!  log=/opt/SVA/server/log.out"
sleep 1
pgrep -af 'Analyzer -f' | head
