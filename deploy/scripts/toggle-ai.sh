#!/usr/bin/env bash
# =====================================================================
# AI 公开接口访问开关（只影响 Nginx，不改后端代码）
#
# 用法：
#   sudo bash deploy/scripts/toggle-ai.sh off   # /api/chat、/api/ai/** 返回 403
#   sudo bash deploy/scripts/toggle-ai.sh on    # 恢复访问（仍有限流）
#
# 原理：站点配置 include /etc/nginx/darunfa-ai.inc；
#   on  -> 复制空白的 ai-on.inc
#   off -> 复制带 return 403 的 ai-off.inc
# 然后 nginx -t + reload。需要 root 或 sudo。
# =====================================================================
set -euo pipefail

MODE="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NGINX_SRC_DIR="$(dirname "$SCRIPT_DIR")/nginx"
TARGET="/etc/nginx/darunfa-ai.inc"

if [ "$(id -u)" -eq 0 ]; then
  SUDO=""
else
  SUDO="sudo"
fi

case "$MODE" in
  on)
    $SUDO cp "$NGINX_SRC_DIR/ai-on.inc" "$TARGET"
    ;;
  off)
    $SUDO cp "$NGINX_SRC_DIR/ai-off.inc" "$TARGET"
    ;;
  *)
    echo "用法: $0 on|off" >&2
    exit 1
    ;;
esac

$SUDO nginx -t
$SUDO systemctl reload nginx
echo "AI public endpoints: $MODE ($TARGET)"
