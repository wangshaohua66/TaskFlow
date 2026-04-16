# TaskFlow Docker 部署指南

## 快速开始

### 使用 Docker 命令

```bash
# 构建镜像
docker build -t taskflow:latest .

# 运行容器
docker run -d \
  --name taskflow \
  -p 8080:8080 \
  -v taskflow-data:/app/data \
  --restart unless-stopped \
  taskflow:latest

# 查看日志
docker logs -f taskflow

# 停止容器
docker stop taskflow

# 删除容器
docker rm taskflow
```

## 访问应用

- **URL**: http://localhost:8080
- **默认账号**: admin / admin123

## 数据持久化

用户数据存储在 `/app/data/users.json`，通过Docker卷持久化：

```bash
# 查看数据卷
docker volume ls | grep taskflow

# 备份数据
docker run --rm -v taskflow-data:/data -v $(pwd):/backup alpine tar czf /backup/taskflow-data-backup.tar.gz -C /data .

# 恢复数据
docker run --rm -v taskflow-data:/data -v $(pwd):/backup alpine tar xzf /backup/taskflow-data-backup.tar.gz -C /data
```

## 环境变量配置

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| JAVA_OPTS | JVM参数 | -Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 |

示例：
```bash
docker run -d \
  --name taskflow \
  -p 8080:8080 \
  -e JAVA_OPTS="-Xms512m -Xmx1g" \
  taskflow:latest
```

## 健康检查

容器内置健康检查，每30秒检查一次应用状态：

```bash
# 查看容器健康状态
docker inspect --format='{{.State.Health.Status}}' taskflow
```

## 多阶段构建说明

Dockerfile使用多阶段构建：
1. **builder阶段**: 使用Maven编译打包应用
2. **runtime阶段**: 使用轻量级JRE镜像运行应用

最终镜像大小约150MB左右。

## 故障排查

```bash
# 查看容器日志
docker logs taskflow

# 进入容器shell
docker exec -it taskflow sh

# 检查容器资源使用
docker stats taskflow
```
