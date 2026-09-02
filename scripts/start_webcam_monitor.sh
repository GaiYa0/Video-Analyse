#!/usr/bin/env bash
set -euo pipefail
API="http://127.0.0.1:9114"
LOGIN_BODY='{"username":"admin","password":"admin123"}'
APE="cam918429"

TOKEN=$(curl -sS -X POST "$API/login" -H "Content-Type: application/json" -d "$LOGIN_BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))")
if [[ -z "$TOKEN" ]]; then
  echo "login failed"
  exit 1
fi

echo "=== start monitor $APE ==="
curl -sS -X POST "$API/waring/device/monitor/$APE/start" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"
echo

mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ easySVA -N -B -e "SELECT monitor_status, play_url FROM h_device WHERE ape_id='$APE';"
