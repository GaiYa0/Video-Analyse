#!/usr/bin/env bash
# 关闭演示服务：backend / WVP / Analyzer / ZLM / 国标模拟器
# 不停 MariaDB / Redis / Nginx
if grep -q $'\r' "$0" 2>/dev/null; then tmp=$(mktemp); tr -d '\r' <"$0" >"$tmp"; exec bash "$tmp" "$@"; fi
set -u
export PATH=/usr/bin:/bin

stop_match() {
  local label="$1"
  shift
  local pat
  local pids=""
  local any=0
  for pat in "$@"; do
    local found
    found=$(pgrep -f "$pat" 2>/dev/null || true)
    if [[ -n "$found" ]]; then
      any=1
      pids="$pids $found"
    fi
  done
  pids=$(echo "$pids" | xargs 2>/dev/null || true)
  if [[ "$any" -eq 0 || -z "$pids" ]]; then
    echo "$label 未在运行"
    return 0
  fi
  # 先温和再强杀；避免只匹配到本脚本命令行
  kill $pids 2>/dev/null || true
  sleep 1
  local left=""
  for pat in "$@"; do
    left="$left $(pgrep -f "$pat" 2>/dev/null || true)"
  done
  left=$(echo "$left" | xargs 2>/dev/null || true)
  if [[ -n "$left" ]]; then
    kill -9 $left 2>/dev/null || true
    sleep 0.5
  fi
  local still=""
  for pat in "$@"; do
    still="$still $(pgrep -f "$pat" 2>/dev/null || true)"
  done
  still=$(echo "$still" | xargs 2>/dev/null || true)
  if [[ -n "$still" ]]; then
    echo "$label 仍未退出 pid=[$still]"
    return 1
  fi
  echo "$label 已停"
  return 0
}

echo "=== 关闭演示服务 ==="
rc=0
stop_match "backend.jar" 'java -jar backend.jar' 'backend.jar --spring' || rc=1
stop_match "国标模拟器" 'gb28181_sim.py' || rc=1
stop_match "WVP" 'wvp-pro-' || rc=1
# Analyzer：优先精确进程名，再兜底绝对路径（勿用裸 Analyzer 模糊串匹配本脚本）
if pgrep -x Analyzer >/dev/null 2>&1 || pgrep -f '/opt/SVA/server/Analyzer' >/dev/null 2>&1; then
  pkill -x Analyzer 2>/dev/null || true
  pkill -f '/opt/SVA/server/Analyzer' 2>/dev/null || true
  sleep 1
  if pgrep -x Analyzer >/dev/null 2>&1 || pgrep -f '/opt/SVA/server/Analyzer' >/dev/null 2>&1; then
    pkill -9 -x Analyzer 2>/dev/null || true
    pkill -9 -f '/opt/SVA/server/Analyzer' 2>/dev/null || true
    sleep 0.5
  fi
  if pgrep -x Analyzer >/dev/null 2>&1 || pgrep -f '/opt/SVA/server/Analyzer' >/dev/null 2>&1; then
    echo "Analyzer 仍未退出: $(pgrep -ax Analyzer 2>/dev/null || pgrep -af '/opt/SVA/server/Analyzer')"
    rc=1
  else
    echo "Analyzer 已停"
  fi
else
  echo "Analyzer 未在运行"
fi
stop_match "MediaServer" 'MediaServer -d' '/opt/SVA/mediaServer/MediaServer' || rc=1

echo "=== 剩余端口（应为空）==="
left_ports=$(ss -tlnp 2>/dev/null | grep -E ':9114|:9992|:18080|:5060' || true)
if [[ -n "$left_ports" ]]; then
  echo "$left_ports"
  rc=1
else
  echo "所有服务端口已关"
fi
echo "完成"
exit "$rc"
