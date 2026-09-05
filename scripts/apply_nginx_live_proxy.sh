#!/bin/bash
# 若在 Windows 上编辑导致 CRLF，先自愈再重新执行
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi

# 把 ZLM 的 /live/ 与国标 /rtp/ 挂到 Nginx 80，复用 Windows 的 8080→80 转发。
# 幂等：缺哪个补哪个。不要改 zlm_server.host。

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

python3 - "$CONF" "$SNIPPET" <<'PY'
import re
import sys

path, snippet_path = sys.argv[1], sys.argv[2]
src = open(path, encoding="utf-8").read()
snippet = open(snippet_path, encoding="utf-8").read().rstrip() + "\n\n"

def has_loc(name: str) -> bool:
    return re.search(rf"location\s+{re.escape(name)}\s*\{{", src) is not None

changed = False
if has_loc("/live/") and has_loc("/rtp/"):
    print("nginx /live/ and /rtp/ already present")
    raise SystemExit(0)

if not has_loc("/live/") and not has_loc("/rtp/"):
    anchor = "        location /websocket/ {"
    if anchor not in src:
        raise SystemExit("websocket location not found in " + path)
    src = src.replace(anchor, snippet + anchor, 1)
    changed = True
else:
    rtp_block = """        location /rtp/ {
            proxy_pass http://127.0.0.1:9992/rtp/;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $http_host;
            proxy_buffering off;
            proxy_read_timeout 600s;
        }

"""
    live_block = """        location /live/ {
            proxy_pass http://127.0.0.1:9992/live/;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $http_host;
            proxy_buffering off;
            proxy_read_timeout 600s;
        }

"""
    if not has_loc("/rtp/"):
        m = re.search(r"        location /live/ \{.*?\n        \}\n", src, re.S)
        if m:
            src = src[: m.end()] + "\n" + rtp_block + src[m.end() :]
        else:
            src = src.replace("        location /websocket/ {", rtp_block + "        location /websocket/ {", 1)
        changed = True
    if not has_loc("/live/"):
        src = src.replace("        location /websocket/ {", live_block + "        location /websocket/ {", 1)
        changed = True

if changed:
    open(path, "w", encoding="utf-8").write(src)
    print("nginx proxy snippet updated")
PY

nginx -t
systemctl reload nginx
echo "nginx /live/ + /rtp/ proxy applied and reloaded"
