#!/usr/bin/env bash
# =====================================================================
# 重置演示环境：把数据库恢复成种子数据的初始状态
#
# 用途：面试官演示时把数据点乱了 / 想从零演示一遍 → 跑这个脚本
# 警告：会删除 mysql-data 卷，所有运行时产生的数据（新订单、新用户）都会消失
#
# 用法：cd /opt/darunfa/deploy && ./scripts/reset-demo.sh
# =====================================================================
set -euo pipefail

COMPOSE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$COMPOSE_DIR"

if [ ! -f .env ]; then
  echo "!! 缺少 .env" >&2
  exit 1
fi

echo "!! 即将删除数据库卷 darunfa_mysql-data，回到种子数据状态。"
read -r -p "确认继续？输入 yes 回车：" ANS
if [ "$ANS" != "yes" ]; then
  echo "已取消"
  exit 0
fi

echo "==> 停止容器"
docker compose down

echo "==> 删除数据卷"
docker volume rm darunfa_mysql-data 2>/dev/null || echo "    （卷不存在，跳过）"

echo "==> 重新启动（会重新导入 deploy/sql/ 下的种子数据）"
docker compose up -d

echo "==> 等待应用就绪"
for i in $(seq 1 45); do
  if curl -fsS -o /dev/null --max-time 3 http://127.0.0.1:8080/login.html; then
    echo "==> 已恢复，用 admin / admin123 登录即可"
    exit 0
  fi
  sleep 2
done

echo "!! 未在预期时间内就绪，请查看日志：docker compose logs --tail=80 app" >&2
exit 1
