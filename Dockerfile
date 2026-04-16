# TaskFlow - Docker多阶段构建文件
# 基于OpenJDK 8和Maven构建Spring Boot应用

# ==================== 第一阶段：构建 ====================
FROM --platform=$BUILDPLATFORM maven:3.9.6-eclipse-temurin-8 AS builder

# 设置工作目录
WORKDIR /app

# 复制pom.xml并下载依赖（利用Docker缓存层）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 执行编译和打包（跳过测试以加快构建速度）
RUN mvn clean package -DskipTests -B

# ==================== 第二阶段：运行 ====================
FROM eclipse-temurin:8-jre

# 设置工作目录
WORKDIR /app

# 创建非root用户运行应用(安全最佳实践)
RUN groupadd -r taskflow && useradd -r -g taskflow taskflow

# 从构建阶段复制jar包
COPY --from=builder /app/target/taskflow-1.0.0.jar app.jar

# 创建数据目录用于存储用户数据
RUN mkdir -p /app/data && chown -R taskflow:taskflow /app

# 暴露应用端口
EXPOSE 8080

# 设置健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

# 切换到非root用户
USER taskflow

# JVM优化参数
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
