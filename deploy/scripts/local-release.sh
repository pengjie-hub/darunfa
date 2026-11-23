#!/usr/bin/env bash
# =====================================================================
# 本地构建 → 上传 → 远程重启（2核2G 机器专用通道）
#
# 为什么要有这个脚本：
#   2G 内存跑不动 npm ci + mvn package，会在构建阶段被 OOM Killer 打断。
#   所以把构建放在你本地电脑上做，服务器只负责 COPY 一个 jar 进镜像。
#
# 用法（在仓库根目录执行，Windows 用 Git Bash）：
#   bash deploy/scripts/local-release.sh root@43.108.56.60
#
# 可选环境变量：
#   REMOTE_DIR   服务器上的项目目录，默认 /opt/darunfa
#   SKIP_BUILD   设为 1 则跳过本地构建，直接上传已有的 deploy/dist/app.jar
# =====================================================================
set -euo pipefail

TARGET="${1:-}"
if [ -z "$TARGET" ]; then
  echo "用法: bash deploy/scripts/local-release.sh <user@host>" >&2
  echo "例如: bash deploy/scripts/local-release.sh root@43.108.56.60" >&2
  exit 1
fi

REMOTE_DIR="${REMOTE_DIR:-/opt/darunfa}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(dirname "$DEPLOY_DIR")"
HOST="${TARGET##*@}"

echo "==> 仓库目录: $REPO_ROOT"
echo "==> 部署目标: $TARGET:$REMOTE_DIR"

# ---------- 前置检查：服务器上得先有代码和可用的 .env ----------
echo "==> [0/4] 检查服务器端目录与 .env"
if ! MSYS_NO_PATHCONV=1 ssh -o ConnectTimeout=10 "$TARGET" "test -f '$REMOTE_DIR/deploy/docker-compose.local.yml'"; then
  cat >&2 <<EOF

!! 服务器上找不到 $REMOTE_DIR/deploy/docker-compose.local.yml

   可能的原因：
   1. 还没在服务器上克隆代码 —— 先执行手册 Stage 3.2：
        mkdir -p /opt && cd /opt
        git clone https://github.com/你的用户名/darunfa.git
   2. 代码是旧版本，缺 docker-compose.local.yml —— 在服务器上执行：
        cd /opt/darunfa && git pull
   3. SSH 连不上 —— 先在本地手动试一下：ssh $TARGET

EOF
  exit 1
fi

# .env 占位符检查：ChangeMe / 请替换 / 假 key 都不允许进入部署
if ! MSYS_NO_PATHCONV=1 ssh "$TARGET" "bash '$REMOTE_DIR/deploy/scripts/check-env.sh'"; then
  echo "!! 服务器端 deploy/.env 未通过 preflight，请先修改占位符" >&2
  exit 1
fi

if [ "${SKIP_BUILD:-0}" = "1" ]; then
  echo "==> 跳过本地构建（SKIP_BUILD=1）"
  if [ ! -f "$DEPLOY_DIR/dist/app.jar" ]; then
    echo "!! deploy/dist/app.jar 不存在，没法跳过构建" >&2
    exit 1
  fi
else
  # ---------- 1. 前端 ----------
  echo "==> [1/4] 构建前端（产物输出到 backend/src/main/resources/static/app）"
  echo "    node $(node -v) / npm $(npm -v)"
  cd "$REPO_ROOT/frontend"
  if [ ! -d node_modules ] || [ package-lock.json -nt node_modules ]; then
    echo "    安装/更新依赖（package-lock 有变化或 node_modules 不存在）"
    npm ci --no-audit --no-fund
  else
    echo "    依赖已是较新状态，跳过 npm ci"
  fi
  npm run build:backend

  # ---------- 2. 后端 ----------
  echo "==> [2/4] 打包 Spring Boot fat jar（跳过测试）"
  cd "$REPO_ROOT/backend"
  # backend/mvnw 在 Git 中是 100644；用 bash 调用，避免 Linux/CI 上 Permission denied
  bash ./mvnw -q -DskipTests package

  JAR="$(ls -1 target/*.jar 2>/dev/null | grep -v '\.original$' | head -n1 || true)"
  if [ -z "$JAR" ]; then
    echo "!! 没找到 target/*.jar，构建可能失败了" >&2
    exit 1
  fi
  echo "    产物: $JAR ($(du -h "$JAR" | cut -f1))"

  mkdir -p "$DEPLOY_DIR/dist"
  cp "$JAR" "$DEPLOY_DIR/dist/app.jar"
fi

# ---------- 3. 上传 ----------
echo "==> [3/4] 上传 jar 到服务器"
# 先建远端目录：deploy/dist 被 .gitignore 忽略，clone 下来不存在
MSYS_NO_PATHCONV=1 ssh "$TARGET" "mkdir -p '$REMOTE_DIR/deploy/dist'"
# 注意：本地路径这一行**不能**加 MSYS_NO_PATHCONV=1。
# Git Bash 需要把 /c/Users/... 自动转成 C:/Users/... 原生 scp 才认得；
# 一旦禁用转换，Windows 的 scp.exe 会把 /c/... 解析成 C:\c\... 而报 No such file。
# 远端路径带 host: 前缀，MSYS 本来就不会转换，无需干预。
scp -q "$DEPLOY_DIR/dist/app.jar" "$TARGET:$REMOTE_DIR/deploy/dist/app.jar"
echo "    已上传 $(du -h "$DEPLOY_DIR/dist/app.jar" | cut -f1)"

# ---------- 4. 远程重建并启动 ----------
echo "==> [4/4] 服务器重建镜像并启动（只 COPY jar，几十秒）"
MSYS_NO_PATHCONV=1 ssh "$TARGET" "cd '$REMOTE_DIR/deploy' && docker compose -f docker-compose.local.yml up -d --build"

echo "==> 等待应用就绪（最多 90 秒）"
if MSYS_NO_PATHCONV=1 ssh "$TARGET" 'for i in $(seq 1 45); do curl -fsS -o /dev/null --max-time 3 http://127.0.0.1:8080/login.html && exit 0; sleep 2; done; exit 1'; then
  echo "==> 应用已就绪"
else
  echo "!! 未在预期时间内就绪，下面是服务器上的日志：" >&2
  MSYS_NO_PATHCONV=1 ssh "$TARGET" "cd '$REMOTE_DIR/deploy' && docker compose -f docker-compose.local.yml logs --tail=80 app" >&2 || true
  exit 1
fi

# /app/ 在 Spring 侧是 302 → /app/index.html，必须用 -L 跟随
if MSYS_NO_PATHCONV=1 ssh "$TARGET" 'curl -fsSL -o /dev/null --max-time 5 http://127.0.0.1:8080/app/'; then
  echo "==> /app/ 可访问（302 跟随）"
else
  echo "!! /app/ 不可访问，请检查 jar 内是否包含 static/app/index.html" >&2
  exit 1
fi

echo "==> 容器状态"
MSYS_NO_PATHCONV=1 ssh "$TARGET" "cd '$REMOTE_DIR/deploy' && docker compose -f docker-compose.local.yml ps"

cat <<EOF

=============================================================
完成。访问地址：

  后台管理   http://$HOST/app/
  传统页面   http://$HOST/login.html

（首次访问如果 502，等 10 秒再刷新，Spring Boot 启动要时间）
=============================================================
EOF
