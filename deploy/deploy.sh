#!/usr/bin/env bash
# =====================================================================
# 服务器端一键更新脚本（放在 /opt/darunfa/deploy/deploy.sh）
# 用法：cd /opt/darunfa/deploy && ./deploy.sh
#
# 做的事：git pull → 重建镜像 → 启动 → 等健康检查 → 打印状态
# =====================================================================
set -euo pipefail

COMPOSE_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$COMPOSE_DIR")"

echo "==> 项目目录: $PROJECT_DIR"

if [ ! -f "$COMPOSE_DIR/.env" ]; then
  echo "!! 缺少 $COMPOSE_DIR/.env，请先 cp .env.example .env 并填写真实值" >&2
  exit 1
fi

cd "$PROJECT_DIR"

if [ -d .git ]; then
  echo "==> 拉取最新代码"
  git pull --ff-only
else
  echo "==> 非 git 目录，跳过 git pull"
fi

cd "$COMPOSE_DIR"

echo "==> 构建并启动容器（首次构建约 5-10 分钟，要下载 Maven 依赖含 160MB 嵌入模型）"
docker compose up -d --build

echo "==> 清理悬空镜像"
docker image prune -f >/dev/null 2>&1 || true

echo "==> 等待应用就绪（最多 90 秒）"
READY=0
for i in $(seq 1 45); do
  if curl -fsS -o /dev/null --max-time 3 http://127.0.0.1:8080/login.html; then
    echo "==> 应用已就绪"
    READY=1
    break
  fi
  sleep 2
done

if [ "$READY" -ne 1 ]; then
  echo "!! 应用未在预期时间内就绪，下面是最近日志：" >&2
  docker compose logs --tail=80 app >&2
  exit 1
fi

echo "==> 当前容器状态"
docker compose ps

echo "==> 最近 20 行应用日志"
docker compose logs --tail=20 app
