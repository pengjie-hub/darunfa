#!/usr/bin/env bash
# =====================================================================
# 每日备份：MySQL 数据 + deploy/.env
#
# 用法：
#   cd /opt/darunfa/deploy && bash ./scripts/backup-db.sh
#   crontab: 30 3 * * * /bin/bash /opt/darunfa/deploy/scripts/backup-db.sh
#
# 2G 机器必须用 docker-compose.local.yml；默认 compose 是源码构建版。
# 备份目录默认 /opt/backup，可用 BACKUP_DIR 覆盖；保留 7 天，可用 RETAIN_DAYS 覆盖。
# =====================================================================
set -euo pipefail

COMPOSE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="docker-compose.local.yml"
BACKUP_DIR="${BACKUP_DIR:-/opt/backup}"
RETAIN_DAYS="${RETAIN_DAYS:-7}"
STAMP="$(date +%F)"

cd "$COMPOSE_DIR"

if [ ! -f .env ]; then
  echo "!! 缺少 $COMPOSE_DIR/.env" >&2
  exit 1
fi
bash ./scripts/check-env.sh

set -a
. ./.env
set +a

mkdir -p "$BACKUP_DIR"

echo "==> 备份数据库到 $BACKUP_DIR/darunfa-$STAMP.sql.gz"
docker compose -f "$COMPOSE_FILE" exec -T mysql \
  mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines darunfa \
  | gzip > "$BACKUP_DIR/darunfa-$STAMP.sql.gz"

echo "==> 备份 .env（含 DB 口令/JWT/DeepSeek key，权限 600）"
cp .env "$BACKUP_DIR/darunfa-env-$STAMP.bak"
chmod 600 "$BACKUP_DIR/darunfa-env-$STAMP.bak"

echo "==> 清理超过 $RETAIN_DAYS 天的旧备份"
find "$BACKUP_DIR" -name 'darunfa-*.sql.gz' -mtime +"$RETAIN_DAYS" -delete
find "$BACKUP_DIR" -name 'darunfa-env-*.bak' -mtime +"$RETAIN_DAYS" -delete

echo "==> 完成："
ls -lh "$BACKUP_DIR"/darunfa-* 2>/dev/null || true
