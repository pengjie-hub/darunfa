# =====================================================================
# 大润发电商系统 —— 前后端一体化镜像
#   Stage 1  构建 Vue 后台（产物输出到 /build/frontend/dist）
#   Stage 2  构建 Spring Boot fat jar，并把前端产物塞进 static/app
#   Stage 3  运行镜像（仅 JRE，非 root 用户）
#
# 构建上下文必须指向仓库根目录：
#   docker build -f deploy/Dockerfile -t darunfa-backend:latest .
# =====================================================================

# ------------------------- Stage 1: 前端构建 -------------------------
FROM node:22-alpine AS web
WORKDIR /build/frontend

# 先只拷贝依赖清单，让 npm ci 这层能被缓存
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY frontend/ ./
# 注意：这里不能用 package.json 里的 build:backend（它写死了 ../backend 相对路径），
# 镜像内要输出到独立目录，再由 Stage 2 拷进 backend 的 static/app。
RUN npx vite build --base=/app/ --outDir=dist

# ------------------------- Stage 2: 后端构建 -------------------------
FROM maven:3.9-eclipse-temurin-17 AS api
WORKDIR /build

COPY backend/pom.xml ./
# 预拉依赖（含 BGE 嵌入模型约 160MB），单独成层便于缓存
RUN mvn -B -q -DskipTests dependency:go-offline

COPY backend/src ./src
COPY --from=web /build/frontend/dist ./src/main/resources/static/app

RUN mvn -B -q -DskipTests package

# ------------------------- Stage 3: 运行镜像 -------------------------
FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system app \
 && useradd --system --gid app --home-dir /app app \
 && mkdir -p /app/logs \
 && chown -R app:app /app

WORKDIR /app
COPY --from=api /build/target/*.jar /app/app.jar
RUN chown app:app /app/app.jar

USER app
EXPOSE 8080

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"

# 用 sh -c 是为了让 $JAVA_OPTS 能被展开；exec 保证 java 成为 PID 1，能收到 SIGTERM 优雅停机
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
