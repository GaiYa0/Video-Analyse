#!/usr/bin/env bash
if grep -q $'\r' "$0" 2>/dev/null; then tmp=$(mktemp); tr -d '\r' <"$0" >"$tmp"; exec bash "$tmp" "$@"; fi
set -euo pipefail
export PATH=/usr/bin:/bin
echo "=== 关闭演示服务 ==="
pkill -f 'backend.jar' && echo "backend.jar 已停" || echo "backend.jar 未在运行"
pkill -f 'gb28181_sim.py' && echo "国标模拟器已停" || echo "国标模拟器未在运行"
pkill -f 'wvp-pro-'   && echo "WVP 已停"         || echo "WVP 未在运行"
pkill -f 'Analyzer'   && echo "Analyzer 已停"     || echo "Analyzer 未在运行"
pkill -f 'MediaServer' && echo "MediaServer 已停"  || echo "MediaServer 未在运行"
echo "=== 剩余端口（应为空）==="
ss -tlnp 2>/dev/null | grep -E '9114|9992|18080|5060' || echo "所有服务端口已关"
echo "完成"
