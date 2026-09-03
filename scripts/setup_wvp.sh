#!/usr/bin/env bash
# 演示机：初始化 WVP 库并生成 easysva 配置（与现网 ZLM 对接）
set -euo pipefail
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi

WVP_HOME="${WVP_HOME:-/opt/wvp-GB28181-pro}"
SQL_DIR="$WVP_HOME/数据库/2.7.4"
MYSQL="mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ"
LAN_IP="${LAN_IP:-$(hostname -I | awk '{print $1}')}"
ZLM_SECRET=$(grep -E '^secret=' /opt/SVA/mediaServer/config.ini | head -1 | cut -d= -f2- | tr -d '\r\n ')
ZLM_ID=$(grep -E '^mediaServerId=' /opt/SVA/mediaServer/config.ini | head -1 | cut -d= -f2- | tr -d '\r\n ')

echo "LAN_IP=$LAN_IP ZLM_ID=$ZLM_ID"
java -version
mvn -v | head -3

$MYSQL -e "CREATE DATABASE IF NOT EXISTS wvp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
if ! $MYSQL -N -e "USE wvp; SHOW TABLES;" | grep -q .; then
  echo "Importing WVP schema..."
  $MYSQL wvp < "$SQL_DIR/初始化-mysql-2.7.4.sql"
else
  echo "WVP tables already exist, skip import"
fi

cat > "$WVP_HOME/src/main/resources/application-easysva.yml" <<EOF
spring:
  mvc:
    async:
      request-timeout: 20000
  thymeleaf:
    cache: false
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 100MB
  cache:
    type: redis
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 7
      password:
      timeout: 10000
  datasource:
    type: com.zaxxer.hikari.HikariDataSource
    driver-class-name: com.mysql.cj.jdbc.Drive
    url: jdbc:mysql://127.0.0.1:3307/wvp?useUnicode=true&characterEncoding=UTF8&rewriteBatchedStatements=true&serverTimezone=PRC&useSSL=false&allowMultiQueries=true&allowPublicKeyRetrieval=true
    username: root
    password: easySVA.EZ

server:
  port: 18080

sip:
  ip: ${LAN_IP}
  show-ip: ${LAN_IP}
  port: 5060
  domain: 3402000000
  id: 34020000002000000001
  password: 12345678
  alarm: false

media:
  id: ${ZLM_ID}
  ip: 127.0.0.1
  hook-ip: 127.0.0.1
  stream-ip: ${LAN_IP}
  sdp-ip: ${LAN_IP}
  http-port: 9992
  secret: ${ZLM_SECRET}
  rtp:
    enable: true
    port-range: 30000,35000
    send-port-range: 30000,35000

user-settings:
  interface-authentication: false
  play-timeout: 180000
  auto-apply-play: true
  record-push-live: false
  record-sip: false
  stream-on-demand: true
EOF

# 激活 easysva profile（覆盖仓库默认 274-dev）
sed -i 's/active: .*/active: easysva/' "$WVP_HOME/src/main/resources/application.yml"

echo "Wrote application-easysva.yml and set profile=easysva"
echo "Next: cd $WVP_HOME && mvn -DskipTests package"
