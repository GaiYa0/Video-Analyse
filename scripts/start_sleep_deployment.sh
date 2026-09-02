#!/usr/bin/env bash
# 启动睡岗布控 controljDNAEPaKnlcupU（工位摄像头）
set -euo pipefail
API="http://127.0.0.1:9114"
LOGIN_BODY='{"username":"admin","password":"admin123"}'
DEPLOY="controljDNAEPaKnlcupU"

TOKEN=$(curl -sS -X POST "$API/login" -H "Content-Type: application/json" -d "$LOGIN_BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))")
echo "=== start $DEPLOY ==="
curl -sS -X POST "$API/deployments/$DEPLOY/start" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{}'
echo
mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ easySVA -N -B -e "SELECT deployment_id, status FROM deployment_task WHERE deployment_id='$DEPLOY'"
