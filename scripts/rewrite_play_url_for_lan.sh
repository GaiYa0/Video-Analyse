#!/bin/bash
# 若在 Windows 上编辑导致 CRLF，先自愈再重新执行
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi

# 把库里的 ws://127.0.0.1:9992/{live|rtp}/... 改成同学能打开的
# ws://<局域网IP>:8080/{live|rtp}/...
# 用法：
#   bash scripts/rewrite_play_url_for_lan.sh 10.21.235.102
# 不要改 zlm_server.host，那会弄断 Analyzer 与后端调 ZLM API。
# Analyzer 只从 play_url 解析 /rtp/<stream> 路径，host 改成局域网不影响布控拉流。

set -euo pipefail

LAN_IP="${1:-}"
if [ -z "$LAN_IP" ]; then
  echo "usage: $0 <lan-ip>"
  echo "example: $0 10.21.235.102"
  exit 1
fi

Q() {
  mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ easySVA -N -B -e "$1"
}

echo "rewrite play_url to ws://${LAN_IP}:8080/live|rtp/..."
Q "update h_device
     set play_url = concat('ws://${LAN_IP}:8080/live/', ape_id, '.live.flv')
   where device_type <> 'gb28181'
     and (play_url like 'ws://127.0.0.1:9992/live/%'
      or play_url like 'ws://%/live/%');"
Q "update h_device
     set play_url = concat('ws://${LAN_IP}:8080',
                           substring(play_url, locate('/rtp/', play_url)))
   where play_url like '%/rtp/%';"
Q "update h_screen_wall_stream
     set play_url = concat('ws://${LAN_IP}:8080',
                           substring(play_url, locate('/live/', play_url)))
   where play_url like '%/live/%';"
Q "update h_screen_wall_stream
     set play_url = concat('ws://${LAN_IP}:8080',
                           substring(play_url, locate('/rtp/', play_url)))
   where play_url like '%/rtp/%';"

echo "h_device:"
Q "select ape_id, name, device_type, play_url from h_device;"
echo "h_screen_wall_stream:"
Q "select id, title, play_url from h_screen_wall_stream;"
