#!/usr/bin/env bash
if grep -q $'\r' "$0" 2>/dev/null; then tmp=$(mktemp); tr -d '\r' <"$0" >"$tmp"; exec bash "$tmp" "$@"; fi
set -euo pipefail

STATIC="${1:-/opt/SVA/wvp/static}"
CSS_SRC="${2:-}"

if [[ -z "$CSS_SRC" ]]; then
  echo "usage: inject_theme.sh <static_dir> <sva-theme.css>" >&2
  exit 1
fi
if [[ ! -f "$CSS_SRC" ]]; then
  echo "sva-theme.css not found: $CSS_SRC" >&2
  exit 1
fi
if [[ ! -d "$STATIC" ]]; then
  echo "static dir not found: $STATIC" >&2
  exit 1
fi

INDEX="$STATIC/index.html"
if [[ ! -f "$INDEX" ]]; then
  echo "index.html not found in $STATIC" >&2
  exit 1
fi

cp "$CSS_SRC" "$STATIC/sva-theme.css"

python3 - "$INDEX" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
html = p.read_text(encoding='utf-8')
marker = 'sva-theme.css'
link = '<link rel="stylesheet" href="/sva-theme.css">'
if marker in html:
    print('sva-theme already linked')
else:
    if '</head>' in html:
        html = html.replace('</head>', link + '\n</head>', 1)
    else:
        html = link + '\n' + html
    p.write_text(html, encoding='utf-8')
    print('sva-theme linked')
PY
