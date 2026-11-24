#!/usr/bin/env bash
# =====================================================================
# 本地构建 → 上传 → 远程重启（2核2G 机器专用通道）
#
# 为什么要有这个脚本：
#   2G 内存跑不动 npm ci + mvn package，会在构建阶段被 OOM Killer 打断。
#   所以把构建放在你本地电脑上做，服务器只负责 COPY 一个 jar 进镜像。
#
# 用法（在仓库根目录执行，Windows 用 Git Bash）：
#   bash deploy/scripts/local-release.sh root@<你的服务器IP>
#
# 可选环境变量：
#   REMOTE_DIR   服务器上的项目目录，默认 /opt/darunfa
#   SKIP_BUILD   设为 1 则跳过本地构建，直接上传已有的 deploy/dist/app.jar
#   SKIP_PULL    设为 1 则跳过服务器端 git pull（默认会先同步 compose/nginx 改动）
# =====================================================================
set -euo pipefail

TARGET="${1:-}"
if [ -z "$TARGET" ]; then
  echo "用法: bash deploy/scripts/local-release.sh <user@host>" >&2
  echo "例如: bash deploy/scripts/local-release.sh root@192.0.2.10" >&2
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

# 同步服务器端仓库：compose / nginx 配置的改动只存在于 git 里，
# 本脚本只负责上传 jar。不同步的话会出现「本地脚本是新的、服务器编排是旧的」
# 这种最难排查的组合（例如 compose 仍在 build，而本地已改成挂载）。
if [ "${SKIP_PULL:-0}" != "1" ]; then
  echo "==> 同步服务器端代码（git pull --ff-only）"
  if ! MSYS_NO_PATHCONV=1 ssh "$TARGET" "cd '$REMOTE_DIR' && git pull --ff-only 2>&1 | tail -3"; then
    echo "!! 服务器端 git pull 失败（可能是本地有改动或分支分叉）" >&2
    echo "   到服务器上手动处理：cd $REMOTE_DIR && git status" >&2
    echo "   确认无误后可用 SKIP_PULL=1 跳过这一步" >&2
    exit 1
  fi
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

# ---------- 4. 远程重启 ----------
# 这一步不构建任何镜像：容器直接用官方 JRE 镜像，jar 由 ./dist:/app/dist:ro 挂载。
# 2G 机器上 buildkit 会被 OOM 杀掉（load local bake definitions 之后报
# "NotFound: forwarding Ping: no such job"），而自建镜像里原本只有 JRE 和环境变量，
# 没有必须构建的内容，所以整条构建链路都去掉了。
echo "==> [4/4] 服务器重启应用容器（不构建镜像，秒级）"
if ! MSYS_NO_PATHCONV=1 ssh "$TARGET" "cd '$REMOTE_DIR/deploy' && docker compose -f docker-compose.local.yml up -d --force-recreate app"; then
  echo "!! 容器启动失败，以下是服务器上的现场信息：" >&2
  MSYS_NO_PATHCONV=1 ssh "$TARGET" "cd '$REMOTE_DIR/deploy' && docker compose -f docker-compose.local.yml ps && docker compose -f docker-compose.local.yml logs --tail=60 app" >&2 || true
  MSYS_NO_PATHCONV=1 ssh "$TARGET" 'free -h; df -h /' >&2 || true
  exit 1
fi

echo "==> 等待应用就绪（首次启动含 MySQL 建库+导入种子，最多等 4 分钟）"
if MSYS_NO_PATHCONV=1 ssh "$TARGET" 'for i in $(seq 1 120); do curl -fsS -o /dev/null --max-time 3 http://127.0.0.1:8080/login.html && exit 0; sleep 2; done; exit 1'; then
  echo "==> 应用已就绪"
else
  echo "!! 4 分钟内未就绪。先看是不是还在建库/拉镜像，不要急着重建：" >&2
  MSYS_NO_PATHCONV=1 ssh "$TARGET" "cd '$REMOTE_DIR/deploy' && docker compose -f docker-compose.local.yml ps && docker compose -f docker-compose.local.yml logs --tail=80 app" >&2 || true
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

# 判断 80 端口有没有接上（Stage 5 配完 Nginx 才会通）
if MSYS_NO_PATHCONV=1 ssh "$TARGET" 'curl -fsS -o /dev/null --max-time 3 http://127.0.0.1/app/' 2>/dev/null; then
  cat <<EOF

=============================================================
完成，公网可以直接访问了：

  后台管理   http://$HOST/app/
  传统页面   http://$HOST/login.html

账号：admin / admin123（或 opsuser / admin123）
=============================================================
EOF
else
  cat <<EOF

=============================================================
应用已在服务器内部就绪（127.0.0.1:8080 返回正常）。

但 80 端口还没有接上 —— 说明 Nginx 还没配（Stage 5 未做），
所以现在从浏览器打开 http://$HOST/app/ 是打不开的，这是预期行为。

临时验证（在服务器上执行）：
  curl -I http://127.0.0.1:8080/app/

下一步：完成 Stage 5（安装并配置 Nginx），公网才能访问。
=============================================================
EOF
fi
