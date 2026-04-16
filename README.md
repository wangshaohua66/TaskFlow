# TaskFlow - 团队协作任务管理系统

## 项目简介

TaskFlow 是一个轻量级的团队协作任务管理系统,帮助团队高效管理项目任务、跟踪进度、协调工作。系统采用前后端分离架构,提供完整的任务生命周期管理功能。

## 核心功能

### 1. 任务管理
- **创建任务**: 支持标题、描述、优先级、截止日期等完整信息
- **任务状态流转**: 待处理 → 进行中 → 已完成/已取消
- **任务分类**: 通过标签对任务进行分类管理
- **任务克隆**: 快速复制相似任务模板

### 2. 子任务系统
- **任务分解**: 将复杂任务拆分为多个子任务
- **依赖管理**: 父任务完成需所有子任务完成
- **循环依赖检测**: 自动防止任务依赖环
- **级联操作**: 智能处理父子任务关系

### 3. 智能提醒
- **过期检测**: 自动识别逾期任务
- **优先级升级**: 长期逾期任务自动提升优先级
- **提醒时间**: 支持设置任务提醒

### 4. 高级搜索
- **多条件过滤**: 按状态、优先级、日期、标签等组合搜索
- **关键词检索**: 全文搜索任务标题和描述
- **分页展示**: 支持大数据量分页浏览

### 5. 统计分析
- **任务统计**: 实时查看各状态任务数量
- **优先级分布**: 了解任务紧急程度分布
- **逾期分析**: 监控任务逾期情况

## 技术栈

### 后端
- **框架**: Spring Boot 3.2
- **语言**: Java 17
- **测试**: JUnit 5 + Mockito
- **构建工具**: Maven

### 前端
- **技术**: 原生 HTML5 + CSS3 + JavaScript
- **通信**: Fetch API + RESTful

### 数据存储
- **存储方式**: 内存存储(适用于小型团队或演示环境)
- **并发控制**: 乐观锁机制

## 快速开始

### 前置要求
- JDK 8 或更高版本
- Maven 3.6+

### 启动应用

```bash
# 编译项目
mvn clean package

# 运行应用
mvn spring-boot:run

# 访问系统
# http://localhost:8080
```

### 开发模式

```bash
# 热重载模式
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

## 项目结构

```
taskflow/
├── src/main/java/com/example/todo/
│   ├── model/                      # 数据模型层
│   │   ├── Todo.java              # 任务实体
│   │   ├── TodoStatus.java        # 任务状态枚举
│   │   ├── Priority.java          # 优先级枚举
│   │   ├── SearchCriteria.java    # 搜索条件
│   │   └── PageResult.java        # 分页结果
│   ├── repository/                 # 数据访问层
│   │   └── TodoRepository.java    # 任务仓库
│   ├── service/                    # 业务逻辑层
│   │   └── TodoService.java       # 任务服务
│   ├── controller/                 # 控制器层
│   │   └── TodoController.java    # REST API控制器
│   ├── exception/                  # 异常定义
│   │   ├── TodoException.java
│   │   ├── ResourceNotFoundException.java
│   │   ├── OptimisticLockException.java
│   │   └── BusinessRuleException.java
│   ├── config/                     # 配置类
│   │   └── GlobalExceptionHandler.java
│   └── TodoApplication.java        # 应用入口
├── src/main/resources/static/      # 静态资源
│   └── index.html                  # 前端页面
├── src/test/java/                  # 单元测试
└── pom.xml                         # Maven配置
```

## API 文档

### 任务管理

#### 创建任务
```http
POST /api/todos
Content-Type: application/json

{
  "title": "完成需求文档",
  "description": "编写产品需求文档初稿",
  "priority": "HIGH",
  "dueDate": "2024-12-31T18:00:00",
  "tags": ["文档", "需求"]
}
```

#### 查询任务列表
```http
GET /api/todos?page=0&size=20
```

#### 查询单个任务
```http
GET /api/todos/{id}
```

#### 更新任务
```http
PUT /api/todos/{id}
Content-Type: application/json

{
  "title": "更新后的标题",
  "version": 1
}
```

#### 删除任务
```http
DELETE /api/todos/{id}
```

### 状态管理

#### 更改任务状态
```http
PATCH /api/todos/{id}/status
Content-Type: application/json

{
  "status": "IN_PROGRESS"
}
```

### 标签管理

#### 添加标签
```http
POST /api/todos/{id}/tags
Content-Type: application/json

{
  "tag": "紧急"
}
```

#### 移除标签
```http
DELETE /api/todos/{id}/tags/{tag}
```

### 子任务管理

#### 添加子任务
```http
POST /api/todos/{parentId}/subtodos
Content-Type: application/json

{
  "subTodoId": 123
}
```

#### 移除子任务
```http
DELETE /api/todos/{parentId}/subtodos/{subTodoId}
```

### 搜索功能

#### 高级搜索
```http
GET /api/todos/search?keyword=需求&status=PENDING&priority=HIGH&page=0&size=20
```

### 批量操作

#### 获取统计信息
```http
GET /api/todos/statistics
```

#### 升级逾期任务优先级
```http
POST /api/todos/upgrade-expired
```

#### 批量删除已完成任务
```http
DELETE /api/todos/completed
```

#### 克隆任务
```http
POST /api/todos/{id}/clone
```

## 业务规则

### 任务状态流转

```
待处理(PENDING)
  ↓
进行中(IN_PROGRESS)
  ↓
已完成(COMPLETED)  或  已取消(CANCELLED)

特殊状态:
- 已过期(EXPIRED): 超过截止日期自动标记
- 已取消的任务可以重新激活为待处理
```

### 状态转换规则

| 当前状态 | 可转换状态 |
|---------|----------|
| 待处理 | 进行中、已取消 |
| 进行中 | 已完成、待处理、已取消 |
| 已完成 | (终态,不可转换) |
| 已取消 | 待处理(重新激活) |
| 已过期 | 进行中、已取消 |

### 子任务规则

1. **完成约束**: 父任务完成前,所有子任务必须已完成
2. **依赖检测**: 系统自动检测并阻止循环依赖
3. **数量限制**: 每个任务最多20个子任务
4. **级联删除**: 删除父任务时会检查子任务完成情况

### 标签规则

1. **数量限制**: 每个任务最多5个标签
2. **唯一性**: 同一任务不能有重复标签
3. **自动格式化**: 标签自动转换为小写

### 优先级规则

| 优先级 | 说明 | 升级路径 |
|-------|------|---------|
| LOW | 低优先级 | → MEDIUM |
| MEDIUM | 中优先级 | → HIGH |
| HIGH | 高优先级 | → CRITICAL |
| CRITICAL | 紧急 | (最高级) |

**自动升级**: 任务逾期超过24小时,优先级自动提升一级

### 并发控制

系统采用**乐观锁**机制:
- 每次更新任务时版本号+1
- 更新时需传入当前版本号
- 版本不匹配时拒绝更新,返回409冲突

## 使用场景

### 场景1: 个人任务管理
- 创建个人待办事项
- 设置优先级和截止日期
- 通过标签分类(工作、生活、学习)

### 场景2: 团队协作
- 项目经理创建主任务
- 拆分为子任务分配给团队成员
- 跟踪整体进度

### 场景3: 项目管理
- 使用标签标记不同项目
- 通过优先级管理紧急程度
- 定期清理已完成任务

### 场景4: 敏捷开发
- 创建Sprint任务看板
- 状态流转: Backlog → In Progress → Done
- 每日站会查看逾期任务

## 部署说明

### 本地开发
```bash
mvn spring-boot:run
```

### 生产部署
```bash
# 打包
mvn clean package -DskipTests

# 运行
java -jar target/todo-test-project-1.0.0.jar
```

### Docker部署(可选)
```dockerfile
FROM openjdk:8-jdk-slim
COPY target/taskflow-project-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
```

## 扩展建议

### 短期优化
1. **数据持久化**: 集成MySQL/PostgreSQL
2. **用户认证**: 添加JWT身份验证
3. **WebSocket**: 实现实时通知
4. **文件附件**: 支持任务附件上传

### 长期规划
1. **微服务改造**: 拆分用户、任务、通知服务
2. **消息队列**: 异步处理邮件通知
3. **缓存优化**: Redis缓存热点数据
4. **监控告警**: 集成Prometheus + Grafana

## 常见问题

### Q: 数据重启后会丢失吗?
A: 当前版本使用内存存储,重启后数据会丢失。生产环境建议集成数据库。

### Q: 支持多用户吗?
A: 当前版本为单用户模式,多用户支持正在开发中。

### Q: 如何备份数据?
A: 可通过API导出所有任务数据为JSON格式。

### Q: 性能如何?
A: 内存存储支持数千任务流畅运行,大数据量建议集成数据库。

## 许可证

MIT License

## 联系方式

如有问题或建议,请提交Issue。
