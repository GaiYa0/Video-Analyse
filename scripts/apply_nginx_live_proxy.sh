#!/bin/bash
# 若在 Windows 上编辑导致 CRLF，先自愈再重新执行
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi

# 把 ZLM 的 /live/ 挂到 Nginx 80，复用 Windows 的 8080→80 转发。
# 幂等：已有 location /live/ 则跳过。

set -euo pipefail

CONF="${NGINX_SITE:-/etc/nginx/sites-available/default}"
SNIPPET_DIR="$(cd "$(dirname "$0")" && pwd)"
SNIPPET="$SNIPPET_DIR/nginx-live-proxy.conf"

if [ ! -f "$CONF" ]; then
  echo "nginx site not found: $CONF"
  exit 1
fi
if [ ! -f "$SNIPPET" ]; then
  echo "snippet not found: $SNIPPET"
  exit 1
fi

if grep -q 'location /live/' "$CONF"; then
  echo "nginx /live/ already present"
  exit 0
fi

python3 - "$CONF" "$SNIPPET" <<'PY'
import sys
path, snippet_path = sys.argv[1], sys.argv[2]
src = open(path, encoding='utf-8').read()
block = open(snippet_path, encoding='utf-8').read().rstrip() + '\n\n'
anchor = '        location /websocket/ {'
if anchor not in src:
    raise SystemExit('websocket location not found in ' + path)
open(path, 'w', encoding='utf-8').write(src.replace(anchor, block + anchor, 1))
PY

nginx -t
systemctl reload nginx
echo "nginx /live/ proxy applied and reloaded"
