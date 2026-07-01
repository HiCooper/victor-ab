# Victor AB Experiment System

<div align="center">

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.0-brightgreen)
![Maven](https://img.shields.io/badge/Maven-3.x-blue)
![License](https://img.shields.io/badge/License-MIT-green)

**维克托 (Victor)** - 企业级 A/B 测试实验平台后端服务

[特性](#-特性) • [架构](#-系统架构) • [快速开始](#-快速开始) • [文档](#-文档) • [API](#-api-接口)

</div>

---

## 📖 项目简介

Victor 是一个功能完整的 A/B 测试实验平台，提供实验管理、流量分配、数据统计分析等核心功能。系统采用分层架构设计，支持多层实验、正交分桶、实时数据分析和统计显著性检验。

### 核心能力

- 🧪 **实验管理**: 创建、更新、启停 A/B 测试实验
- 🎯 **流量分配**: 基于 MurmurHash3 的一致性哈希分桶算法
- 📊 **统计分析**: Z-Test、mSPRT、CUPED、BH校正等统计算法
- 🔄 **版本控制**: 实验分桶版本管理和灰度发布
- ⚡ **高性能**: Redis缓存 + Caffeine本地缓存双层缓存架构
- 📈 **实时监控**: Kafka事件流 + ClickHouse实时数据分析

---

## ✨ 特性

### 实验管理

- ✅ 支持多层实验（Layer）和正交分桶
- ✅ 实验状态管理（草稿、运行中、已结束）
- ✅ 实验分桶（Bucket）配置和版本控制
- ✅ 实验ID自动生成（日期+随机数格式）

### 流量分配

- ✅ 基于用户ID/设备ID的一致性哈希分桶
- ✅ 支持多层实验流量隔离
- ✅ 白名单和黑名单机制
- ✅ 实时分桶结果查询

### 数据分析

- ✅ Z-Test 显著性检验
- ✅ mSPRT 序贯检验
- ✅ CUPED 方差缩减
- ✅ BH 多重检验校正
- ✅ A/A 测试验证
- ✅ 时间序列数据分析

### 技术架构

- ✅ 微服务模块化设计
- ✅ Flyway 数据库版本管理
- ✅ MyBatis-Plus ORM框架
- ✅ SpringDoc OpenAPI 文档
- ✅ Docker 容器化部署

---

## 🏗️ 系统架构

### 模块结构

```
victor-ab/
├── victor-common/    # 公共模块：分桶引擎(BucketEngine/LayerTrafficAllocator)、MurmurHash3、常量、枚举、工具类
├── victor-domain/    # 领域模型：实体、DTO、事件
├── victor-sdk/       # 客户端 SDK：本地分桶 + 配置拉取(离线容灾) + 事件异步上报
├── victor-service/   # 业务与基础设施（单模块，内部按包划分）：
│   ├── service/      #   实验/分桶/配置/统计分析/灰度/RBAC 等业务服务
│   ├── infra/        #   MyBatis-Plus Mapper 与数据访问配置
│   ├── pipeline/     #   事件采集 → Kafka → ClickHouse 数据管道
│   └── stats/        #   统计算法(Z-Test、mSPRT、CUPED、SRM、BH…)与统计引擎
└── victor-starter/   # 启动与 Web 层：Controller、安全(JWT/RBAC)、拦截器(限流/权限/API Key)、配置、主类
```

> 说明：`service / infra / pipeline / stats` 是 `victor-service` 模块内部的包；所有 REST Controller 位于 `victor-starter`。

### 技术栈

| 类别          | 技术                             |
|-------------|--------------------------------|
| **后端框架**    | Spring Boot 3.4.0, Java 17     |
| **ORM框架**   | MyBatis-Plus 3.5.15            |
| **数据库**     | MySQL 8.0, Redis 7, ClickHouse |
| **消息队列**    | Apache Kafka                   |
| **缓存**      | Caffeine (本地), Redis (分布式)     |
| **数据迁移**    | Flyway 9.5.1                   |
| **HTTP客户端** | OkHttp 4.12.0                  |
| **JSON处理**  | Jackson 2.17.0                 |
| **API文档**   | SpringDoc OpenAPI 2.5.0        |
| **构建工具**    | Maven 3.x                      |
| **容器化**     | Docker, Docker Compose         |

---

## 🚀 快速开始

### 前置要求

- JDK 17+
- Maven 3.6+
- Docker & Docker Compose (可选，用于容器化部署)

### 方式一：本地开发环境

#### 1. 启动依赖服务

使用 Docker Compose 启动 MySQL 和 Redis：

```bash
docker-compose up -d mysql redis
```

或者使用开发环境配置（包含更多服务）：

```bash
docker-compose -f docker-compose-dev.yml up -d
```

#### 2. 初始化数据库

数据库会自动通过 Flyway 进行迁移，也可以手动执行：

#### 3. 编译项目

```bash
mvn clean install
```

#### 4. 启动应用

```bash
cd victor-starter
mvn spring-boot:run
```

或者直接运行主类：`com.gateflow.victor.VictorServiceApplication`

#### 5. 访问应用

- **API文档**: http://localhost:8081/swagger-ui.html
- **健康检查**: http://localhost:8081/actuator/health

### 方式二：Docker 部署

#### 1. 构建镜像

```bash
docker build -t victor-service .
```

#### 2. 启动所有服务

```bash
docker-compose up -d
```

这将启动：

- MySQL (端口 3306)
- Redis (端口 6379)
- Victor Service (端口 8081)

#### 3. 查看日志

```bash
docker-compose logs -f victor-service
```

---

## 📚 文档

### 核心文档

- [实验ID生成器使用说明](docs/EXPERIMENT_ID_GENERATOR_USAGE.md) - 实验ID生成规则和最佳实践
- [数据库变更日志](docs/DB_CHANGELOG.md) - 数据库版本变更记录

### API 文档

启动应用后访问 Swagger UI：

```
http://localhost:8081/swagger-ui.html
```

### 主要 API 接口

> 管理端接口位于 `/api/v1/admin/**`，需 JWT 认证 + RBAC 权限；SDK 面向接口位于
> `/api/v1/config`、`/api/v1/bucketing`、`/api/v1/events`（限流，可选 API Key 校验）。

#### 实验管理（`/api/v1/admin/experiments`）

| 方法     | 路径                                     | 描述           |
|--------|----------------------------------------|--------------|
| POST   | `/api/v1/admin/experiments`            | 创建实验         |
| GET    | `/api/v1/admin/experiments/page`       | 分页查询实验列表     |
| GET    | `/api/v1/admin/experiments/{id}`       | 获取实验详情(含活跃分桶) |
| PUT    | `/api/v1/admin/experiments/{id}`       | 更新实验(带分桶则建新版本) |
| DELETE | `/api/v1/admin/experiments/{id}`       | 删除实验         |
| POST   | `/api/v1/admin/experiments/{id}/start` | 启动实验         |
| POST   | `/api/v1/admin/experiments/{id}/stop`  | 停止实验         |
| POST   | `/api/v1/admin/experiments/{id}/submit`\|`/approve`\|`/reject` | 提交审批/审批通过/驳回 |
| POST   | `/api/v1/admin/experiments/{id}/clone` | 克隆实验         |

#### 流量分配（SDK，`/api/v1/bucketing`）

| 方法  | 路径                              | 描述           |
|-----|---------------------------------|--------------|
| GET | `/api/v1/bucketing/bucket`      | 查询用户在指定实验的分桶 |
| GET | `/api/v1/bucketing/all-buckets` | 查询用户所有运行中实验的分桶 |

#### 层级 / 分桶管理

| 方法   | 路径                          | 描述      |
|------|-----------------------------|---------|
| GET/POST/PUT | `/api/v1/admin/layers`      | 层级增查改  |
| GET/POST/PUT/DELETE | `/api/v1/admin/buckets`     | 分桶增查改删 |

#### 统计分析（`/api/v1/admin`）

| 方法  | 路径                                                  | 描述       |
|-----|-----------------------------------------------------|----------|
| GET | `/api/v1/admin/reports/...`                         | 实验统计报告   |
| GET | `/api/v1/admin/metrics/realtime`\|`/daily`          | 实时/每日指标  |
| GET | `/api/v1/admin/experiments/{id}/aa-test`            | A/A 测试结果 |

#### 配置下发（SDK，`/api/v1/config`）

| 方法  | 路径                        | 描述                    |
|-----|---------------------------|-----------------------|
| GET | `/api/v1/config/version`  | 比对配置版本(有更新 200 / 无 304) |
| GET | `/api/v1/config/fetch`    | 拉取全量配置                |
| POST| `/api/v1/events`          | SDK 事件批量上报            |

---

## 🔧 配置说明

### 应用配置

主要配置文件位于 `victor-starter/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/victor_experiment
    username: root
    password: victor123
  data:
    redis:
      host: localhost
      port: 6379

server:
  port: 8081
```

### 环境变量

支持通过环境变量覆盖配置：

```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/victor_experiment
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=root
export SPRING_DATA_REDIS_HOST=redis
export SPRING_DATA_REDIS_PORT=6379
```

---

## 📊 数据库设计

### 核心表结构

- **victor_layer**: 实验层级表
- **victor_experiment**: 实验表
- **victor_bucket**: 实验分桶表
- **victor_user_assignment**: 用户分配记录表
- **victor_config_version**: 配置版本表

详见 [数据库变更日志](docs/DB_CHANGELOG.md) 和 Flyway 迁移脚本。

---

## 🧪 测试

### 运行所有测试

```bash
mvn test
```

### 运行特定模块测试

```bash
# 分桶引擎 / 层内分配测试（common 模块）
mvn test -pl victor-common

# 业务、统计算法、数据管道测试（service 模块）
mvn test -pl victor-service

# Web / 安全 / 控制器测试（starter 模块）
mvn test -pl victor-starter
```

### 测试覆盖

- ✅ 分桶算法单元测试
- ✅ 统计算法验证测试
- ✅ Service层集成测试
- ✅ Controller层API测试

---

## 📦 客户端 SDK

### Maven 依赖

```xml

<dependency>
    <groupId>com.gateflow</groupId>
    <artifactId>victor-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 使用示例

```java
import com.gateflow.victor.sdk.VictorClient;
import com.gateflow.victor.sdk.VictorConfig;

// 创建客户端
VictorConfig config = VictorConfig.builder()
        .baseUrl("http://localhost:8081")
        .apiKey("your-api-key")
        .build();

        VictorClient client = new VictorClient(config);

        // 获取用户分桶结果
        BucketingResponse response = client.assignBucket(
                BucketingRequest.builder()
                        .userId("user_123")
                        .experimentId("exp_001")
                        .build()
        );

System.out.

        println("Assigned bucket: "+response.getBucketKey());
```

---

## 🛠️ 开发指南

### 代码规范

- 遵循阿里巴巴 Java 开发手册
- 使用 Lombok 简化代码
- 使用 MapStruct 进行对象映射
- 统一异常处理

### 分支策略

- `main`: 主分支，生产环境代码
- `develop`: 开发分支
- `feature/*`: 功能分支
- `hotfix/*`: 热修复分支

### 提交规范

```
feat: 新功能
fix: 修复bug
docs: 文档更新
style: 代码格式
refactor: 重构
test: 测试相关
chore: 构建/工具链
```

---

## 🐛 故障排查

### 常见问题

#### 1. 数据库连接失败

```bash
# 检查 MySQL 是否启动
docker ps | grep mysql

# 查看日志
docker logs victor-mysql
```

#### 2. Redis 连接失败

```bash
# 检查 Redis 是否启动
docker ps | grep redis

# 测试连接
docker exec -it victor-redis redis-cli ping
```

#### 3. 端口冲突

修改 `docker-compose.yml` 或 `application.yml` 中的端口配置。

---

## 📝 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 👥 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📮 联系方式

- **项目主页**: [GitHub Repository]
- **问题反馈**: [GitHub Issues]
- **邮箱**: support@gateflow.com

---

<div align="center">

**Made with ❤️ by Gateflow Team**

[⬆ 回到顶部](#victor-ab-experiment-system)

</div>