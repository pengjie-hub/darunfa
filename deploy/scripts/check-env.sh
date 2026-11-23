#!/usr/bin/env bash
# =====================================================================
# deploy/.env 上线前检查
#
# 目的：.env.example 给的默认值都是「非空但无效」的占位串，
#       docker compose 的 ${VAR:?} 只拦得住「空 / 未设置」，拦不住「占位符没改」。
#       所以在构建、启动、重置之前先跑这个脚本。
#
# ⚠️ 重要：只检查 KEY=VALUE 的「值」部分，注释和说明文字一律跳过。
#    上一版用 grep 扫整个文件，结果被自己注释里的示例词误伤
#    （.env.example 注释里写着「拒绝 ChangeMe / 请替换」），
#    导致值改对了也永远报错。这里改成逐键取值再判断。
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
  echo "   先执行: cp deploy/.env.example deploy/.env && nano deploy/.env" >&2
  exit 1
fi

# 取某个 KEY 的值：只匹配 KEY=VALUE 行，取最后一条，并去掉可能的 CR
env_get() {
  sed -n "s/^[[:space:]]*$1=//p" "$ENV_FILE" | tail -n 1 | tr -d '\r'
}

fail=0

# Windows 上编辑过的 .env 会带 CRLF，\r 会被当成密码/key 的一部分
if grep -q $'\r' "$ENV_FILE" 2>/dev/null; then
  echo "!! $ENV_FILE 含 Windows 换行（CRLF），\\r 会被当成值的一部分导致连库或鉴权失败" >&2
  echo "   修复: sed -i 's/\\r\$//' $ENV_FILE" >&2
  fail=1
fi

for key in MYSQL_ROOT_PASSWORD JWT_SECRET ADMIN_PASSWORD DEEPSEEK_API_KEY; do
  value="$(env_get "$key")"
  if [ -z "$value" ]; then
    echo "!! $key 为空或缺失，请在 $ENV_FILE 中填写真实值" >&2
    fail=1
    continue
  fi
  # 只看这一行的值；注释里的说明文字不参与判断
  case "$value" in
    *ChangeMe*|*请替换*|*sk-你的*)
      echo "!! $key 仍是占位值: $value" >&2
      fail=1
      ;;
  esac
done

JWT_VALUE="$(env_get JWT_SECRET)"
JWT_LEN="$(printf '%s' "$JWT_VALUE" | wc -c | tr -d ' ')"
if [ "${JWT_LEN:-0}" -lt 32 ]; then
  echo "!! JWT_SECRET 只有 ${JWT_LEN} 个字符，至少需要 32 个" >&2
  echo "   生成: openssl rand -base64 48" >&2
  fail=1
fi

if [ "$fail" -ne 0 ]; then
  echo "" >&2
  echo "!! 未通过。改完 $ENV_FILE 后重跑：bash deploy/scripts/check-env.sh" >&2
  exit 1
fi

echo "env ok: $ENV_FILE"
