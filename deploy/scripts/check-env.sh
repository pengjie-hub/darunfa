#!/usr/bin/env bash
# =====================================================================
# deploy/.env 上线前检查
#
# 目的：.env.example 里的 ChangeMe / sk-请替换 都是非空值，
#       docker compose 的 ${VAR:?} 只能拦“空/未设置”，拦不住“占位符没改”。
#       在构建、启动、重置之前先跑这个脚本，避免带着弱口令/假 key 上线。
#
# 用法：
#   bash deploy/scripts/check-env.sh
#   bash deploy/scripts/check-env.sh /opt/darunfa/deploy/.env
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${1:-$SCRIPT_DIR/../.env}"

if [ ! -f "$ENV_FILE" ]; then
  echo "!! 找不到 $ENV_FILE" >&2
  echo "   先执行: cp deploy/.env.example deploy/.env && vim deploy/.env" >&2
  exit 1
fi

fail=0

for key in MYSQL_ROOT_PASSWORD JWT_SECRET ADMIN_PASSWORD DEEPSEEK_API_KEY; do
  value="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true)"
  if [ -z "${value:-}" ]; then
    echo "!! $key 为空，请在 $ENV_FILE 中填写真实值" >&2
    fail=1
  fi
done

if grep -Eq 'ChangeMe|请替换|sk-你的' "$ENV_FILE"; then
  echo "!! $ENV_FILE 里仍有 ChangeMe / 请替换 / sk-你的 等占位符" >&2
  echo "   请把四个值都改成真实值后再继续" >&2
  fail=1
fi

JWT_VALUE="$(grep -E '^JWT_SECRET=' "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true)"
JWT_LEN="$(printf '%s' "${JWT_VALUE:-}" | wc -c | tr -d ' ')"
if [ "${JWT_LEN:-0}" -lt 32 ]; then
  echo "!! JWT_SECRET 只有 ${JWT_LEN} 个字符，至少需要 32 个" >&2
  echo "   生成: openssl rand -base64 48" >&2
  fail=1
fi

if [ "$fail" -ne 0 ]; then
  exit 1
fi

echo "env ok: $ENV_FILE"
