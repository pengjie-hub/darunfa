#!/usr/bin/env bash
# =====================================================================
# 重置演示环境：把数据库恢复成种子数据的初始状态
#
# 用途：面试官演示时把数据点乱了 / 想从零演示一遍 → 跑这个脚本
# 警告：会删除 mysql-data 卷，所有运行时产生的数据（新订单、新用户）都会消失
#
# 用法：
#   cd /opt/darunfa/deploy && bash ./scripts/reset-demo.sh
#   cd /opt/darunfa/deploy && bash ./scripts/reset-demo.sh --yes   # 供 cron 夜间重置
#
# 注意：2G 机器必须用 docker-compose.local.yml；默认的 docker-compose.yml
#       会在服务器上从源码构建（Node + Maven），2G 大概率被 OOM Killer 打断。
# =====================================================================
set -euo pipefail

COMPOSE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="docker-compose.local.yml"
VOLUME_NAME="darunfa_mysql-data"
ASSUME_YES=0

if [ "${1:-}" = "--yes" ]; then
  ASSUME_YES=1
fi

cd "$COMPOSE_DIR"

if [ ! -f .env ]; then
  echo "!! 缺少 .env" >&2
  exit 1
fi

# 与 local-release.sh / deploy.sh 共用同一套 .env 占位符检查
bash ./scripts/check-env.sh

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "!! 找不到 $COMPOSE_DIR/$COMPOSE_FILE" >&2
  exit 1
fi

echo "!! 即将删除数据库卷 $VOLUME_NAME，回到种子数据状态。"
if [ "$ASSUME_YES" -ne 1 ]; then
  read -r -p "确认继续？输入 yes 回车：" ANS
  if [ "$ANS" != "yes" ]; then
    echo "已取消"
    exit 0
  fi
fi

echo "==> 停止容器"
docker compose -f "$COMPOSE_FILE" down

echo "==> 删除数据卷"
if docker volume inspect "$VOLUME_NAME" >/dev/null 2>&1; then
  docker volume rm "$VOLUME_NAME"
else
  echo "    （卷不存在，跳过）"
fi
if docker volume inspect "$VOLUME_NAME" >/dev/null 2>&1; then
  echo "!! 卷 $VOLUME_NAME 仍然存在，重置未生效；请 docker volume ls | grep darunfa 确认实际卷名" >&2
  exit 1
fi

echo "==> 重新启动（会重新导入 deploy/sql/ 下的种子数据）"
docker compose -f "$COMPOSE_FILE" up -d

echo "==> 等待应用就绪"
READY=0
for i in $(seq 1 45); do
  # /app/ 在 Spring 侧是 302 → /app/index.html，必须 -L 跟随
  if curl -fsSL -o /dev/null --max-time 3 http://127.0.0.1:8080/app/; then
    READY=1
    echo "==> 已恢复。公开演示建议用 opsuser / admin123，不要把 admin 口令写进简历。"
    echo "    （重置后种子里的 admin 会回到 admin123，如公开 admin 请再次改密）"
    break
  fi
  sleep 2
done

if [ "$READY" -ne 1 ]; then
  echo "!! 未在预期时间内就绪，请查看日志：docker compose -f $COMPOSE_FILE logs --tail=80 app" >&2
  exit 1
fi
