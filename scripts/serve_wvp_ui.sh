#!/usr/bin/env bash
if grep -q $'\r' "$0" 2>/dev/null; then tmp=$(mktemp); tr -d '\r' <"$0" >"$tmp"; exec bash "$tmp" "$@"; fi
set -euo pipefail
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:/usr/bin:/bin"

SRC=/opt/wvp-GB28181-pro/src/main/resources/static
rm -rf /opt/SVA/wvp/static
mkdir -p /opt/SVA/wvp/static
cp -a "$SRC"/. /opt/SVA/wvp/static/
ls /opt/SVA/wvp/static | head
head -c 300 /opt/SVA/wvp/static/index.html; echo

# merge static locations into easysva or separate file already
cat > /opt/SVA/wvp/config/application-static.yml <<'EOF'
spring:
  web:
    resources:
      static-locations: file:/opt/SVA/wvp/static/,classpath:/META-INF/resources/,classpath:/resources/,classpath:/static/,classpath:/public/
EOF

# activate static profile together with easysva
pkill -f 'wvp-pro-' || true
sleep 2
JAR=$(ls /opt/wvp-GB28181-pro/target/wvp-pro-*.jar | grep -v original | head -1)
cd /opt/SVA/wvp
nohup java -jar "$JAR" \
  --spring.profiles.active=easysva,static \
  --spring.config.additional-location=optional:file:/opt/SVA/wvp/config/ \
  > /opt/SVA/wvp/wvp.log 2>&1 &
echo $! > /opt/SVA/wvp/wvp.pid

for i in $(seq 1 40); do
  code=$(curl --noproxy 127.0.0.1 -sS -o /tmp/wvp_idx.out -w '%{http_code}' http://127.0.0.1:18080/ || true)
  if ss -tlnp | grep -q ':18080'; then
    echo "port_up code=$code"
    head -c 250 /tmp/wvp_idx.out; echo
    if [[ "$code" == "200" ]] && grep -qiE 'html|app|wvp|script' /tmp/wvp_idx.out; then
      echo WVP_UI_OK
      exit 0
    fi
  fi
  sleep 2
done
tail -50 /opt/SVA/wvp/wvp.log
exit 1
