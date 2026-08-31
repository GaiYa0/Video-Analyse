#!/bin/bash
# 演示机日常启动脚本（同学 A）
# Windows: wsl -d Ubuntu-22.04 -u root -- bash /mnt/d/video-analysis/Video-Analyse-main/scripts/start_easysva.sh
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

echo "=== [1/6] MariaDB on 3307 ==="
if ! grep -q '^port = 3307' /etc/mysql/mariadb.conf.d/50-server.cnf; then
  sed -i '/^\[mysqld\]/a port = 3307' /etc/mysql/mariadb.conf.d/50-server.cnf
fi
systemctl start mariadb
sleep 2
systemctl is-active mariadb

echo "=== [2/6] Database init ==="
mysql -uroot -peasySVA.EZ -h127.0.0.1 -P3307 -e "CREATE DATABASE IF NOT EXISTS easySVA DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if ! mysql -uroot -peasySVA.EZ -h127.0.0.1 -P3307 -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='easySVA';" | grep -qv '^0$'; then
  mysql -uroot -peasySVA.EZ -h127.0.0.1 -P3307 easySVA < /opt/data_20250520.sql
fi

echo "=== [3/6] Start nginx ==="
systemctl start nginx
systemctl is-active nginx

echo "=== [4/6] Start backend ==="
pkill -f 'backend.jar' 2>/dev/null || true
sleep 2
cd /opt/SVA/backend
: > log.out
nohup java -jar backend.jar \
  --spring.datasource.druid.master.url='jdbc:mysql://127.0.0.1:3307/easySVA?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8' \
  > log.out 2>&1 &

for i in $(seq 1 40); do
  if ss -tlnp | grep -q ':9114'; then
    echo "backend listening on 9114"
    break
  fi
  if grep -q 'Started RuoYiApplication' log.out 2>/dev/null; then
    echo "backend started"
    break
  fi
  if grep -q 'Application run failed' log.out 2>/dev/null; then
    echo "backend failed"
    tail -20 log.out
    exit 1
  fi
  sleep 3
done

echo "=== [5/6] Start media server and analyzer ==="
if ! pgrep -f './MediaServer -d' >/dev/null; then
  cd /opt/SVA/mediaServer && ./MediaServer -d
fi
sleep 2
if ! pgrep -f './Analyzer -f' >/dev/null; then
  cd /opt/SVA/server
  : > log.out
  nohup ./Analyzer -f /opt/SVA/config.json > log.out 2>&1 &
fi

echo "=== [6/6] Health check ==="
curl -s -o /dev/null -w "web:%{http_code}\n" http://127.0.0.1:80/
curl -s -o /dev/null -w "backend:%{http_code}\n" http://127.0.0.1:9114/
ss -tlnp | grep -E '3307|80|9114' || true
ps aux | grep -E 'backend.jar|MediaServer|Analyzer' | grep -v grep
