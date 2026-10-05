#!/usr/bin/env bash
# reset.sh —— 重置运行时状态（白名单默认保留）
# 用法：bash release/reset.sh [--all]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> 停止本地服务（如运行中）"
# EXAMPLE: 实现后补充（pkill/ docker compose down）

echo "==> 清空运行时数据"
rm -rf "$ROOT/agent/data/index" "$ROOT/agent/data/sessions"   # EXAMPLE 路径随实现调整
rm -rf "$ROOT/skill/workdir"/*                                 # apktool 工作目录

if [[ "${1:-}" == "--all" ]]; then
  echo "==> [全清] 同时清空白名单与拦截日志（需二次确认）"
  read -r -p "确认全清? 输入 YES: " c && [[ "$c" == "YES" ]] || { echo "已取消"; exit 1; }
  rm -f "$ROOT/agent/data/whitelist.db" "$ROOT/agent/data/audit.db"   # EXAMPLE
fi

echo "==> 完成。重启服务后按 release/RUNNING.md 第6节做冒烟验证。"
