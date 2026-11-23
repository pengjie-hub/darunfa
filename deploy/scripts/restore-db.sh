#!/usr/bin/env bash
# =====================================================================
# 从 backup-db.sh 的产物恢复数据库
#
# 用法：
#   cd /opt/darunfa/deploy && bash ./scripts/restore-db.sh /opt/backup/darunfa-2026-11-23.sql.gz
#   bash ./scripts/restore-db.sh /opt/backup/darunfa-2026-11-23.sql.gz --yes
#
# 警告：会覆盖 darunfa 库现有数据；执行前请确认已备份。
# 2G 机器必须用 docker-compose.local.yml。
# =====================================================================
set -euo pipefail

DUMP="${1:-}"
ASSUME_YES=0
if [ "${2:-}" = "--yes" ]; then
  ASSUME_YES=1
fi

if [ -z "$DUMP" ]; then
  echo "用法: $0 <dump.sql|dump.sql.gz> [--yes]" >&2
  exit 1
fi
if [ ! -f "$DUMP" ]; then
  echo "!! 找不到备份文件: $DUMP" >&2
  exit 1
fi

COMPOSE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="docker-compose.local.yml"
cd "$COMPOSE_DIR"

if [ ! -f .env ]; then
  echo "!! 缺少 $COMPOSE_DIR/.env" >&2
  exit 1
fi
bash ./scripts/check-env.sh

set -a
. ./.env
set +a

echo "!! 即将用 $DUMP 覆盖 darunfa 库；现有数据会被替换。"
if [ "$ASSUME_YES" -ne 1 ]; then
  read -r -p "确认继续？输入 yes 回车：" ANS
  if [ "$ANS" != "yes" ]; then
    echo "已取消"
    exit 0
  fi
fi

echo "==> 停止 app（避免恢复期间写入）"
docker compose -f "$COMPOSE_FILE" stop app 2>/dev/null || true

echo "==> 导入备份"
case "$DUMP" in
  *.gz) gunzip -c "$DUMP" \
          | docker compose -f "$COMPOSE_FILE" exec -T mysql \
              mysql -uroot -p"$MYSQL_ROOT_PASSWORD" darunfa ;;
  *)    cat "$DUMP" \
          | docker compose -f "$COMPOSE_FILE" exec -T mysql \
              mysql -uroot -p"$MYSQL_ROOT_PASSWORD" darunfa ;;
esac

echo "==> 启动 app"
docker compose -f "$COMPOSE_FILE" start app

echo "==> 等待 /app/ 可访问"
for i in $(seq 1 45); do
  if curl -fsSL -o /dev/null --max-time 3 http://127.0.0.1:8080/app/; then
    echo "==> 恢复完成"
    exit 0
  fi
  sleep 2
done

echo "!! 恢复后 app 未在预期时间内就绪，请查看日志：docker compose -f $COMPOSE_FILE logs --tail=80 app" >&2
exit 1
