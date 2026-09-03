#!/usr/bin/env bash
# 向 WVP 注入/查询演示设备；真机 SIP 注册成功后不必用本脚本。
if grep -q $'\r' "$0" 2>/dev/null; then tmp=$(mktemp); tr -d '\r' <"$0" >"$tmp"; exec bash "$tmp" "$@"; fi
set -euo pipefail
export PATH=/usr/bin:/bin
BASE="${WVP_BASE:-http://127.0.0.1:18080}"
DEVICE_ID="${1:-34020000001320000001}"
NOW=$(date '+%Y-%m-%d %H:%M:%S')
LAN_IP=$(hostname -I | awk '{print $1}')

mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ wvp <<SQL
INSERT INTO wvp_device (
  device_id, name, manufacturer, model, firmware, transport, stream_mode,
  on_line, ip, create_time, update_time, port, expires,
  host_address, charset, geo_coord_sys, media_server_id, custom_name, password,
  sdp_ip, local_ip, server_id
) VALUES (
  '${DEVICE_ID}', '演示国标IPC', 'Demo', 'IPC', '1.0', 'UDP', 'TCP-PASSIVE',
  1, '${LAN_IP}', '${NOW}', '${NOW}', 5060, 3600,
  '${LAN_IP}:5060', 'GB2312', 'WGS84', 'Aodpt9CbTmrRBOMv', '演示国标IPC', '12345678',
  '${LAN_IP}', '${LAN_IP}', '34020000002000000001'
) ON DUPLICATE KEY UPDATE on_line=1, name=VALUES(name), update_time=VALUES(update_time);
SQL

mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ -e "SELECT device_id,name,on_line FROM wvp.wvp_device WHERE device_id='${DEVICE_ID}';"
echo "SIP 真机注册：服务器=${LAN_IP}:5060 平台ID=34020000002000000001 密码=12345678"
echo "然后网页点「同步国标设备」或: curl -X POST http://127.0.0.1:9114/waring/device/syncGb28181 -H \"Authorization: Bearer <token>\""
