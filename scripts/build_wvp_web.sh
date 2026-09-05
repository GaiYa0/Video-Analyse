#!/usr/bin/env bash
if grep -q $'\r' "$0" 2>/dev/null; then tmp=$(mktemp); tr -d '\r' <"$0" >"$tmp"; exec bash "$tmp" "$@"; fi
set -euo pipefail
export PATH="/usr/bin:/bin:/usr/local/bin"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

cd /opt/wvp-GB28181-pro/web
if [[ ! -d node_modules ]]; then
  npm install --registry=https://registry.npmmirror.com
fi
npm run build:prod

rm -rf /opt/SVA/wvp/static
mkdir -p /opt/SVA/wvp/static
cp -a dist/. /opt/SVA/wvp/static/

# 写入静态资源配置
cat > /opt/SVA/wvp/config/application-static.yml <<'EOF'
spring:
  web:
    resources:
      static-locations: file:/opt/SVA/wvp/static/,classpath:/META-INF/resources/,classpath:/resources/,classpath:/static/,classpath:/public/
  mvc:
    static-path-pattern: /**
EOF

# 确保 easysva 仍关闭接口鉴权（演示）
python3 - <<'PY'
from pathlib import Path
p = Path('/opt/SVA/wvp/config/application-easysva.yml')
t = p.read_text(encoding='utf-8')
if 'interface-authentication' not in t:
    t = t.replace('user-settings:\n', 'user-settings:\n  interface-authentication: false\n', 1)
    p.write_text(t, encoding='utf-8')
print('easysva ok')
PY

pkill -f 'wvp-pro-' || true
sleep 2
JAR=$(ls /opt/wvp-GB28181-pro/target/wvp-pro-*.jar | grep -v original | head -1)
cd /opt/SVA/wvp
nohup java -jar "$JAR" \
  --spring.profiles.active=easysva \
  --spring.config.additional-location=optional:file:/opt/SVA/wvp/config/ \
  > /opt/SVA/wvp/wvp.log 2>&1 &
echo $! > /opt/SVA/wvp/wvp.pid

for i in $(seq 1 40); do
  code=$(curl --noproxy 127.0.0.1 -sS -o /tmp/wvp_idx.out -w '%{http_code}' http://127.0.0.1:18080/ || true)
  if [[ "$code" == "200" ]] && grep -qi 'html\|vue\|wvp\|login' /tmp/wvp_idx.out; then
    echo "WVP_UI_OK http://127.0.0.1:18080/ code=$code"
    head -c 200 /tmp/wvp_idx.out; echo
    exit 0
  fi
  # 即使还没 UI，端口起来也继续等
  ss -tlnp | grep -q ':18080' || true
  sleep 2
done
echo "timeout code=$code"
tail -40 /opt/SVA/wvp/wvp.log
exit 1
