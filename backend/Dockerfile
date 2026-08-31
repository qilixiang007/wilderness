# ============================================================
# 「宇宙是旷野」后端镜像：多阶段构建
#   1. maven 镜像拉依赖 + 打 jar
#   2. JRE 镜像运行（体积小）
# ============================================================

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先只拷 pom,利用 Docker 层缓存避免每次重下依赖
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# healthcheck 需要 curl
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/backend-0.1.0.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS=""
# 使用 shell 形式以便 JAVA_OPTS 可注入 JVM 参数
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
