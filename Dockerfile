# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
# 先拉依赖，利用 Docker 层缓存
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

# 上传图片目录
RUN mkdir -p /app/upload
ENV UPLOAD_DIR=/app/upload \
    MYSQL_HOST=mysql \
    REDIS_HOST=redis

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
