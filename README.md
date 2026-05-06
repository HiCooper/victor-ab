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
- 🔄 **版本控制**: 实验变体版本管理和灰度发布
- ⚡ **高性能**: Redis缓存 + Caffeine本地缓存双层缓存架构
- 📈 **实时监控**: Kafka事件流 + ClickHouse实时数据分析

---

## ✨ 特性

### 实验管理
- ✅ 支持多层实验（Layer）和正交分桶
- ✅ 实验状态管理（草稿、运行中、已结束）
- ✅ 实验变体（Variant）配置和版本控制
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
├── victor-common/          # 公共模块：常量、枚举、工具类
├── victor-domain/          # 领域模型：实体、DTO、事件
├── victor-bucketing/       # 分桶引擎：流量分配算法
├── victor-infrastructure/  # 基础设施：数据访问、缓存、迁移
├── victor-service/         # 业务服务：实验、分桶、统计服务
├── victor-sdk/             # 客户端SDK：Java SDK
├── victor-pipeline/        # 数据管道：Kafka消费、ClickHouse写入
├── victor-stats/           # 统计引擎：统计算法和模型
└── victor-web/             # Web层：REST API控制器
```

### 技术栈

| 类别 | 技术 |
|------|------|
| **后端框架** | Spring Boot 3.4.0, Java 17 |
| **ORM框架** | MyBatis-Plus 3.5.15 |
| **数据库** | MySQL 8.0, Redis 7, ClickHouse |
| **消息队列** | Apache Kafka |
| **缓存** | Caffeine (本地), Redis (分布式) |
| **数据迁移** | Flyway 9.5.1 |
| **HTTP客户端** | OkHttp 4.12.0 |
| **JSON处理** | Jackson 2.17.0 |
| **API文档** | SpringDoc OpenAPI 2.5.0 |
| **构建工具** | Maven 3.x |
| **容器化** | Docker, Docker Compose |

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

```bash
mysql -h localhost -u root -pvictor123 victor_experiment < init-db-simple.sql
```

#### 3. 编译项目

```bash
mvn clean install
```

#### 4. 启动应用

```bash
cd victor-web
mvn spring-boot:run
```

或者直接运行主类：`com.gateflow.victor.VictorServiceApplication`

#### 5. 访问应用

- **API文档**: http://localhost:8080/swagger-ui.html
- **健康检查**: http://localhost:8080/actuator/health

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
- Victor Service (端口 8080)

#### 3. 查看日志

```bash
docker-compose logs -f victor-service
```

---

## 📚 文档

### 核心文档

- [实验ID生成器使用说明](docs/EXPERIMENT_ID_GENERATOR_USAGE.md) - 实验ID生成规则和最佳实践
- [数据库变更日志](DB_CHANGELOG.md) - 数据库版本变更记录

### API 文档

启动应用后访问 Swagger UI：

```
http://localhost:8080/swagger-ui.html
```

### 主要 API 接口

#### 实验管理

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/experiments` | 创建实验 |
| GET | `/api/experiments` | 查询实验列表 |
| GET | `/api/experiments/{id}` | 获取实验详情 |
| PUT | `/api/experiments/{id}` | 更新实验 |
| DELETE | `/api/experiments/{id}` | 删除实验 |
| POST | `/api/experiments/{id}/start` | 启动实验 |
| POST | `/api/experiments/{id}/stop` | 停止实验 |

#### 流量分配

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/bucketing/assign` | 用户分桶 |
| GET | `/api/bucketing/statistics` | 分桶统计 |

#### 层级管理

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/layers` | 创建层级 |
| GET | `/api/layers` | 查询层级列表 |
| PUT | `/api/layers/{id}` | 更新层级 |

#### 变体管理

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/variants` | 创建变体 |
| GET | `/api/variants/{id}` | 获取变体详情 |
| PUT | `/api/variants/{id}` | 更新变体 |

#### 统计分析

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/experiments/{id}/statistics` | 实验统计数据 |
| GET | `/api/experiments/{id}/metrics` | 实验指标数据 |
| GET | `/api/experiments/{id}/aa-test` | A/A 测试结果 |

#### 配置管理

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/config/latest` | 获取最新配置 |
| GET | `/api/config/version/{version}` | 获取指定版本配置 |

---

## 🔧 配置说明

### 应用配置

主要配置文件位于 `victor-web/src/main/resources/application.yml`：

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
  port: 8080
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
- **victor_variant**: 实验变体表
- **victor_user_assignment**: 用户分配记录表
- **victor_config_version**: 配置版本表

详见 [数据库变更日志](DB_CHANGELOG.md) 和 Flyway 迁移脚本。

---

## 🧪 测试

### 运行所有测试

```bash
mvn test
```

### 运行特定模块测试

```bash
# 分桶引擎测试
mvn test -pl victor-bucketing

# 统计算法测试
mvn test -pl victor-stats

# Web层测试
mvn test -pl victor-web
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
    .baseUrl("http://localhost:8080")
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

System.out.println("Assigned variant: " + response.getVariantKey());
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