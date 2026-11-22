#!/usr/bin/env bash
# =====================================================================
# 清理并校验种子数据 SQL
#
# 背景：docs/darunfa_seed_20261023.sql 的开头混入了当时 mysqldump 在
#       PowerShell 里产生的报错文本（"mysqldump.exe : ... NativeCommandError"），
#       直接导入 MySQL 会在第一行报 ERROR 1064。
#
# 用法（在仓库根目录或任意位置执行均可）：
#   bash deploy/scripts/prepare-seed.sh
# 产物：
#   deploy/sql/01-darunfa-seed.sql   ← MySQL 容器首次启动时自动导入
# =====================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$REPO_ROOT/docs/darunfa_seed_20261023.sql"
OUT_DIR="$REPO_ROOT/deploy/sql"
OUT="$OUT_DIR/01-darunfa-seed.sql"

if [ ! -f "$SRC" ]; then
  echo "!! 找不到源文件: $SRC" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"

echo "==> 从第一个 '-- MySQL dump' 标记处开始截取，丢弃前面的 PowerShell 报错"
sed -n '/^-- MySQL dump/,$p' "$SRC" > "$OUT"

# --- 校验 ---
FIRST_LINE="$(head -n 1 "$OUT")"
case "$FIRST_LINE" in
  --\ MySQL\ dump*) echo "==> 头部校验通过" ;;
  *) echo "!! 头部仍不正确，第一行是: $FIRST_LINE" >&2; exit 1 ;;
esac

if grep -qi "NativeCommandError\|CategoryInfo" "$OUT"; then
  echo "!! 文件内仍残留 PowerShell 报错文本，请手动检查" >&2
  exit 1
fi

INSERTS="$(grep -ci "^INSERT INTO" "$OUT" || true)"
TABLES="$(grep -ci "^CREATE TABLE" "$OUT" || true)"
SIZE="$(wc -c < "$OUT" | tr -d ' ')"

echo "==> 完成: $OUT"
echo "    大小: $SIZE 字节 | 建表: $TABLES 条 | 数据插入: $INSERTS 条"

if [ "$INSERTS" -lt 1 ]; then
  echo "!! 没有检测到 INSERT 语句，导入后可能只有表结构没有演示数据" >&2
  exit 1
fi
