#!/bin/bash
# 若在 Windows 上编辑导致 CRLF，先自愈再重新执行（必须紧接 shebang）
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi

# 演示机日常启动脚本（同学 A）
# Windows 一键：双击或在 PowerShell 运行同目录 start_easysva.ps1
# 或：wsl -d Ubuntu-22.04 -u root -- bash /mnt/e/video-analysis/Video-Analyse-main/scripts/start_easysva.sh
# 附带测试推流：.../start_easysva.sh --with-stream

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

WITH_STREAM=0
if [ "${1:-}" = "--with-stream" ]; then
  WITH_STREAM=1
fi

export DEBIAN_FRONTEND=noninteractive

JDBC_URL='jdbc:mysql://127.0.0.1:3307/easySVA?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8'
TEST_VIDEO='/opt/easySVA-lib/opencv/doc/js_tutorials/js_assets/cup.mp4'
RTMP_URL='rtmp://127.0.0.1:9995/live/test1'

start_systemd() {
  local unit="$1"
  if systemctl is-active --quiet "$unit"; then
    echo "$unit: already active"
  else
    systemctl start "$unit"
    systemctl is-active "$unit"
  fi
}

echo "=== [1/7] MariaDB on 3307 ==="
if ! grep -q '^port = 3307' /etc/mysql/mariadb.conf.d/50-server.cnf; then
  sed -i '/^\[mysqld\]/a port = 3307' /etc/mysql/mariadb.conf.d/50-server.cnf
fi
start_systemd mariadb
sleep 2

echo "=== [2/7] Database init ==="
mysql -uroot -peasySVA.EZ -h127.0.0.1 -P3307 -e "CREATE DATABASE IF NOT EXISTS easySVA DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if ! mysql -uroot -peasySVA.EZ -h127.0.0.1 -P3307 -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='easySVA';" | grep -qv '^0$'; then
  mysql -uroot -peasySVA.EZ -h127.0.0.1 -P3307 easySVA < /opt/data_20250520.sql
fi

echo "=== [3/7] Redis + nginx ==="
start_systemd redis-server || start_systemd redis
start_systemd nginx
bash "$SCRIPT_DIR/apply_nginx_live_proxy.sh"

echo "=== [4/7] Start backend ==="
if pgrep -f 'backend.jar' >/dev/null; then
  echo "backend.jar already running, restarting..."
  pkill -f 'backend.jar' || true
  sleep 3
fi
cd /opt/SVA/backend
: > log.out
nohup java -jar backend.jar \
  --spring.datasource.druid.master.url="$JDBC_URL" \
  > log.out 2>&1 &

backend_ok=0
for _ in $(seq 1 40); do
  if ss -tlnp | grep -q ':9114'; then
    echo "backend listening on 9114"
    backend_ok=1
    break
  fi
  if grep -q 'Started RuoYiApplication' log.out 2>/dev/null; then
    echo "backend started"
    backend_ok=1
    break
  fi
  if grep -q 'Application run failed' log.out 2>/dev/null; then
    echo "backend failed"
    tail -20 log.out
    exit 1
  fi
  sleep 3
done
if [ "$backend_ok" -eq 0 ]; then
  echo "backend did not become ready within timeout"
  tail -20 log.out
  exit 1
fi

echo "=== [5/7] Start media server and analyzer ==="
if ! pgrep -f 'MediaServer' >/dev/null; then
  cd /opt/SVA/mediaServer && ./MediaServer -d
else
  echo "MediaServer: already running"
fi
sleep 2
if ! pgrep -f 'Analyzer -f' >/dev/null; then
  cd /opt/SVA/server
  : > log.out
  nohup ./Analyzer -f /opt/SVA/config.json > log.out 2>&1 &
else
  echo "Analyzer: already running"
fi

if [ "$WITH_STREAM" -eq 1 ]; then
  echo "=== [6/7] Test RTMP push (--with-stream) ==="
  if pgrep -f 'ffmpeg.*9995/live/test1' >/dev/null; then
    echo "ffmpeg test stream: already running"
  elif [ -f "$TEST_VIDEO" ]; then
    nohup ffmpeg -re -stream_loop -1 \
      -i "$TEST_VIDEO" \
      -c:v libx264 -preset veryfast -tune zerolatency -an \
      -f flv "$RTMP_URL" \
      > /tmp/ffmpeg_push.log 2>&1 &
    echo "ffmpeg pushing to $RTMP_URL"
  else
    echo "skip ffmpeg: $TEST_VIDEO not found"
  fi
else
  echo "=== [6/7] Skip test stream (use --with-stream to enable) ==="
fi

echo "=== [7/7] Health check ==="
curl -s -o /dev/null -w "web:%{http_code}\n" http://127.0.0.1:80/
curl -s -o /dev/null -w "backend:%{http_code}\n" http://127.0.0.1:9114/
ss -tlnp | grep -E '3307|6379|80|9114|9992' || true
ps aux | grep -E 'backend.jar|MediaServer|Analyzer' | grep -v grep || true

echo ""
echo "本机访问: http://localhost/  (admin / admin123)"
echo "WSL IP:   http://$(hostname -I | awk '{print $1}')/"
echo "同学访问: http://<你的 Windows 局域网 IP>/  (ipconfig 查看 IPv4)"
