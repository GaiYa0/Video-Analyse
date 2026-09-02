#!/usr/bin/env bash
# 将水杯（cup）布控改为 A-SERVER，停止后重启，并等待新告警验证 main.mp4
set -euo pipefail

MYSQL=(mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ easySVA)
API="http://127.0.0.1:9114"
TOKEN=""

log() { echo "[switch-a-server] $*"; }

get_token() {
  local resp
  resp=$(curl -sS -X POST "${API}/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin123"}')
  TOKEN=$(echo "$resp" | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null || true)
  if [[ -z "$TOKEN" ]]; then
    echo "登录失败: $resp" >&2
    exit 1
  fi
}

api_post() {
  curl -sS -X POST "$1" -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' "${@:2}"
}

echo "=== 当前布控 ==="
"${MYSQL[@]}" -e "SELECT deployment_id, device_id, control_code, record_engine, status FROM deployment_task;"

# 找 cup 相关布控：control_code 含 cup，或算法目标含 cup
DEPLOY_ID=$("${MYSQL[@]}" -N -e "
SELECT dt.deployment_id
FROM deployment_task dt
LEFT JOIN deployment_task_algorithm dta ON dt.deployment_id = dta.deployment_id
WHERE dt.control_code LIKE '%cup%'
   OR dta.target_classes LIKE '%cup%'
   OR dt.deployment_name LIKE '%杯%'
   OR dt.deployment_name LIKE '%cup%'
GROUP BY dt.deployment_id
ORDER BY dt.deployment_id DESC
LIMIT 1;")

if [[ -z "$DEPLOY_ID" ]]; then
  log "未找到 cup 布控，使用最新一条布控"
  DEPLOY_ID=$("${MYSQL[@]}" -N -e "SELECT deployment_id FROM deployment_task ORDER BY deployment_id DESC LIMIT 1;")
fi

if [[ -z "$DEPLOY_ID" ]]; then
  log "无布控记录，请先创建水杯布控"
  exit 1
fi

log "目标布控 deployment_id=${DEPLOY_ID}"

CURRENT=$("${MYSQL[@]}" -N -e "SELECT record_engine, status FROM deployment_task WHERE deployment_id='${DEPLOY_ID}';")
log "当前: ${CURRENT}"

if [[ "$(echo "$CURRENT" | awk '{print $1}')" != "A-SERVER" ]]; then
  log "更新 record_engine -> A-SERVER"
  "${MYSQL[@]}" -e "UPDATE deployment_task SET record_engine='A-SERVER' WHERE deployment_id='${DEPLOY_ID}';"
else
  log "已是 A-SERVER"
fi

get_token

STATUS=$(echo "$CURRENT" | awk '{print $2}')
if [[ "$STATUS" == "RUNNING" ]]; then
  log "停止布控 ${DEPLOY_ID}"
  api_post "${API}/deployments/${DEPLOY_ID}/stop" -d '{}' | python3 -m json.tool 2>/dev/null || true
  sleep 3
fi

log "启动布控 ${DEPLOY_ID}"
START_RESP=$(api_post "${API}/deployments/${DEPLOY_ID}/start" -d '{}')
echo "$START_RESP" | python3 -m json.tool 2>/dev/null || echo "$START_RESP"

sleep 2
"${MYSQL[@]}" -e "SELECT deployment_id, device_id, control_code, record_engine, status FROM deployment_task WHERE deployment_id='${DEPLOY_ID}';"

# 记录切换前最新告警 id
BEFORE_ALARM=$("${MYSQL[@]}" -N -e "SELECT COALESCE(MAX(id),0) FROM h_waring;")
log "切换前最大告警 id=${BEFORE_ALARM}，等待新告警（最多 120s）..."

for i in $(seq 1 24); do
  sleep 5
  NEW=$("${MYSQL[@]}" -N -e "SELECT id FROM h_waring WHERE id > ${BEFORE_ALARM} ORDER BY id DESC LIMIT 1;")
  if [[ -n "$NEW" ]]; then
    log "检测到新告警 id=${NEW}"
    for j in $(seq 1 12); do
      sleep 5
      ROW=$("${MYSQL[@]}" -N -e "
        SELECT id, video_url, sva_media_status, picture_url
        FROM h_waring WHERE id=${NEW};")
      log "告警状态: ${ROW}"
      VIDEO_URL=$(echo "$ROW" | awk '{print $2}')
      if [[ -n "$VIDEO_URL" && "$VIDEO_URL" != "NULL" ]]; then
        MP4="/var/www/SVA-web/upload/${VIDEO_URL}"
        if [[ -f "$MP4" ]]; then
          log "SUCCESS: 磁盘存在 ${MP4} ($(du -h "$MP4" | awk '{print $1}'))"
          exit 0
        fi
        if [[ "$VIDEO_URL" == alarm/* ]]; then
          MP4="/var/www/SVA-web/upload/${VIDEO_URL}"
          if [[ -f "$MP4" ]]; then
            log "SUCCESS: ${MP4}"
            exit 0
          fi
        fi
      fi
      # 也扫描 alarm 目录下该告警
      FOUND=$(find /var/www/SVA-web/upload/alarm -name main.mp4 -newer /tmp/switch_a_server_marker 2>/dev/null | head -1 || true)
    done
    break
  fi
  log "等待告警... (${i}/24)"
done

touch /tmp/switch_a_server_marker 2>/dev/null || true

echo
echo "=== 最近告警 ==="
"${MYSQL[@]}" -e "SELECT id, device_id, control_code, video_url, sva_media_status, sva_media_error, alarm_time FROM h_waring ORDER BY alarm_time DESC LIMIT 5;"

echo
echo "=== alarm/ 下 main.mp4 ==="
find /var/www/SVA-web/upload/alarm -name 'main.mp4' 2>/dev/null | tail -10

echo
echo "=== Analyzer 最近日志 ==="
tail -30 /opt/SVA/server/log.out 2>/dev/null || true

echo
echo "=== 后端素材回调日志 ==="
grep -E 'SVA素材|addFromSvaMedia|saveVideo' /opt/SVA/backend/log.out 2>/dev/null | tail -15 || true

log "未在超时内确认 main.mp4；请确认测试流与布控 RUNNING 后手动复测"
