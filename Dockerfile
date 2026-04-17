# 多阶段构建 Dockerfile for TaskFlow Spring Boot 应用

# 第一阶段: 构建阶段
FROM maven:3.8.6-openjdk-8-slim AS builder

# 设置工作目录
WORKDIR /app

# 复制 pom.xml 和下载依赖 (利用 Docker 缓存层)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用 (跳过测试，测试已在 CI 中执行)
RUN mvn clean package -DskipTests -B

# 第二阶段: 运行阶段
FROM eclipse-temurin:8-jre

# 设置工作目录
WORKDIR /app

# 创建非 root 用户运行应用 (安全最佳实践)
RUN groupadd -r taskflow && useradd -r -g taskflow taskflow

# 从构建阶段复制 jar 文件
COPY --from=builder /app/target/*.jar app.jar

# 更改文件所有者
RUN chown -R taskflow:taskflow /app

# 切换到非 root 用户
USER taskflow

# 暴露应用端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
