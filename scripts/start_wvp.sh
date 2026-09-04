#!/usr/bin/env bash
# 启动 / 重启 WVP（SIP 5060）并写 ZLM hook
set -euo pipefail
if grep -q $'\r' "$0" 2>/dev/null; then
  sed -i 's/\r$//' "$0"
  exec bash "$0" "$@"
fi

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:/usr/bin:/bin"

WVP_HOME="${WVP_HOME:-/opt/wvp-GB28181-pro}"
JAR=$(ls -1 "$WVP_HOME"/target/wvp-pro-*.jarr 2>/dev/null | grep -v original | head -1 || true)
[[ -n "$JAR" ]] || { echo "缺少 jar，先 bash scripts/build_wvp.sh"; exit 1; }

mkdir -p /opt/SVA/wvp/config
# 外部配置（jar 内可能不含 yml）
if [[ -f "$WVP_HOME/src/main/resources/application-easysva.yml" ]]; then
  cp "$WVP_HOME/src/main/resources/application.yml" /opt/SVA/wvp/config/ 2>/dev/null || true
  cp "$WVP_HOME/src/main/resources/application-easysva.yml" /opt/SVA/wvp/config/
fi
[[ -f /opt/SVA/wvp/config/application-easysva.yml ]] || {
  echo "缺少 /opt/SVA/wvp/config/application-easysva.yml，先 bash scripts/setup_wvp.sh"
  exit 1
}

pkill -f 'wvp-pro-' || true
sleep 1
cd /opt/SVA/wvp
nohup java -jar "$JAR" \
  --spring.profiles.active=easysva \
  --spring.config.additional-location=optional:file:/opt/SVA/wvp/config/ \
  > /opt/SVA/wvp/wvp.log 2>&1 &
echo $! > /opt/SVA/wvp/wvp.pid
echo "WVP pid=$(cat /opt/SVA/wvp/wvp.pid)"

# ZLM hook → WVP
INI=/opt/SVA/mediaServer/config.ini
if [[ -f "$INI" ]]; then
  [[ -f "$INI.bak.pre-wvp" ]] || cp "$INI" "$INI.bak.pre-wvp"
  python3 - <<'PY'
from pathlib import Path
p = Path("/opt/SVA/mediaServer/config.ini")
lines = p.read_text(encoding="utf-8", errors="ignore").splitlines()
out=[]; in_hook=False
hooks={
  "enable":"enable=1",
  "on_server_started":"on_server_started=http://127.0.0.1:18080/index/hook/on_server_started",
  "on_stream_changed":"on_stream_changed=http://127.0.0.1:18080/index/hook/on_stream_changed",
  "on_stream_none_reader":"on_stream_none_reader=http://127.0.0.1:18080/index/hook/on_stream_none_reader",
  "on_stream_not_found":"on_stream_not_found=http://127.0.0.1:18080/index/hook/on_stream_not_found",
  "on_play":"on_play=http://127.0.0.1:18080/index/hook/on_play",
  "on_publish":"on_publish=http://127.0.0.1:18080/index/hook/on_publish",
}
for line in lines:
    s=line.strip()
    if s.startswith("[") and s.endswith("]"):
        in_hook=(s.lower()=="[hook]")
        out.append(line); continue
    if in_hook and "=" in s:
        key=s.split("=",1)[0].strip().lower()
        if key in hooks:
            out.append(hooks[key]); continue
    out.append(line)
p.write_text("\n".join(out)+"\n", encoding="utf-8")
print("ZLM hook -> WVP :18080")
PY
fi

for i in $(seq 1 40); do
  if ss -tlnp 2>/dev/null | grep -q ':18080'; then
    echo "WVP HTTP :18080 OK；SIP 见下行"
    ss -tulnp 2>/dev/null | grep -E '5060|18080' || true
    echo "管理页 http://127.0.0.1:18080/  账号 admin / admin（API 密码为 MD5）"
    exit 0
  fi
  sleep 2
done
tail -50 /opt/SVA/wvp/wvp.log
exit 1
