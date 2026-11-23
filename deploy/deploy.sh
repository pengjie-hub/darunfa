#!/usr/bin/env bash
# =====================================================================
# 服务器端一键更新脚本（放在 /opt/darunfa/deploy/deploy.sh）
# 用法：cd /opt/darunfa/deploy && bash ./deploy.sh
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

# 拒绝 ChangeMe / 请替换 / 假 key，避免带弱口令启动
bash "$COMPOSE_DIR/scripts/check-env.sh"

cd "$PROJECT_DIR"

if [ -d .git ]; then
  echo "==> 拉取最新代码"
  git pull --ff-only
else
  echo "==> 非 git 目录，跳过 git pull"
fi

cd "$COMPOSE_DIR"

echo "==> 在服务器上构建并启动容器"

# 2G 机器上 npm + maven 构建大概率 OOM，先提醒一句
MEM_MB=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo 2>/dev/null || echo 0)
if [ "$MEM_MB" -gt 0 ] && [ "$MEM_MB" -lt 3500 ]; then
  echo "!! 检测到内存约 ${MEM_MB}MB，这台机器直接构建容易触发 OOM Killer"
  echo "   更稳的做法是走本地构建通道（在你自己的电脑上执行）："
  echo "     bash deploy/scripts/local-release.sh root@<服务器公网IP>"
  echo "   如果坚持在服务器上构建，10 秒后自动继续（Ctrl+C 取消）"
  sleep 10
fi

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

# /app/ 在 Spring 侧是 302 → /app/index.html，必须 -L 跟随
if curl -fsSL -o /dev/null --max-time 5 http://127.0.0.1:8080/app/; then
  echo "==> /app/ 可访问（302 跟随）"
else
  echo "!! /app/ 不可访问，请检查 jar 内是否包含 static/app/index.html" >&2
  exit 1
fi

echo "==> 当前容器状态"
docker compose ps

echo "==> 最近 20 行应用日志"
docker compose logs --tail=20 app
