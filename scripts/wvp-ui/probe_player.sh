#!/usr/bin/env bash
if grep -q $'\r' "$0" 2>/dev/null; then tmp=$(mktemp); tr -d '\r' <"$0" >"$tmp"; exec bash "$tmp" "$@"; fi
set -euo pipefail

# 同学 A 在演示机执行，把完整输出贴到 PR：
#   bash scripts/wvp-ui/probe_player.sh
# 有路径后 C 再写 patch_player.sh。本脚本不改任何文件。

ROOT="${1:-/opt/wvp-GB28181-pro/web}"
SRC="$ROOT/src"

echo "WVP_WEB_ROOT=$ROOT"
if [[ ! -d "$SRC" ]]; then
  echo "src not found: $SRC" >&2
  exit 1
fi

PATTERN='jessibuca|createPlayer|flv\.js|mpegts|enableWorker|enableStashBuffer'

if command -v rg >/dev/null 2>&1; then
  rg -n "$PATTERN" "$SRC" --glob '!**/node_modules/**' || true
else
  grep -RInE "$PATTERN" "$SRC" --exclude-dir=node_modules || true
fi

echo "WVP_PROBE_DONE"
