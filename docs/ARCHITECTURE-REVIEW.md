# Victor AB 实验平台 — 架构审阅报告

**审阅日期**: 2026-05-08
**审阅范围**: 后端 Java Spring Boot 服务 (`victor-ab`)
**总体评分**: 3.5 / 5.0（优化前 3.1）

---

## 一、当前架构概述

### 1.1 模块结构

```
victor-ab/
|-- victor-common          公共常量、枚举、异常、工具类
|-- victor-domain          领域实体、DTO、事件模型
|-- victor-bucketing       分桶引擎（纯 Java，无 Spring）
|-- victor-infrastructure  数据访问层（MyBatis-Plus、Redis/Kafka）
|-- victor-service         业务服务层
|-- victor-sdk             客户端 Java SDK
|-- victor-pipeline        数据管道（Kafka 消费、ClickHouse 写入）
|-- victor-stats           统计算法引擎（Z-Test、mSPRT、CUPED、BH）
|-- victor-web             Web 入口层（Controller、Spring Boot 启动）
```

### 1.2 核心优势

1. **分桶引擎纯 Java 实现**：`victor-bucketing` 无 Spring 依赖，可直接嵌入 SDK
2. **清晰的模块依赖层次**：严格自底向上，无循环依赖
3. **完整的实验生命周期状态机**：8 种状态，集中管理状态转换
4. **丰富的统计算法**：Z-Test、mSPRT、CUPED、BH、SRM
5. **优秀的 SDK 设计**：本地 Caffeine 缓存 + 定时轮询 + 版本比对

---

## 二、已完成的优化

### P0 — 关键修复

| 编号 | 问题 | 改动 | 涉及文件 |
|------|------|------|---------|
| C1 | StatisticsService 使用模拟数据 | 重写为调用 StatsEngine + MetricsRepository 查询真实 ClickHouse 数据 | `StatisticsService.java` |
| C2 | 安全配置硬编码 | 数据库/ClickHouse 密码改为环境变量，CORS 域名配置化 | `application.yml`, `WebConfig.java`, `.env.example` |
| H1 | 全局异常处理缺失 | 新增 GlobalExceptionHandler 统一错误响应格式 | `GlobalExceptionHandler.java` |

### P1 — 高优先级

| 编号 | 问题 | 改动 | 涉及文件 |
|------|------|------|---------|
| H2 | Controller 层包含业务逻辑 | 将 `calculateBucketBoundaries` 移至 Service 层 | `ExperimentController.java`, `ExperimentService.java` |
| H3 | N+1 数据库查询 | 新增 `selectByIds` 和 `selectActiveVariantsByExpIds` 批量查询 | `LayerMapper.java`, `VariantMapper.java`, `BucketingService.java` |
| H4 | 实验桶范围冲突检测 | 新增 `checkLayerBucketConflict` 方法，创建实验时自动检测 | `ExperimentService.java` |

---

## 三、待优化问题

### 严重级别：High（高）

#### H5. ClickHouseWriter 确认

`ClickHouseWriter` 已确认存在且实现完整。Kafka 消费链路正常。

### 严重级别：Medium（中）

| 编号 | 问题 | 预估工作量 | 建议 |
|------|------|-----------|------|
| M1 | Redis 缓存缺少击穿/雪崩防护 | 1 天 | 使用分布式锁 + TTL |
| M2 | 统计模块依赖 Spring | 1 天 | 统计算法抽为纯 Java 模块 |
| M3 | 错误码体系 | 1 天 | 定义 ErrorCode 枚举 |
| M4 | SDK 优雅关闭 | 0.5 天 | 实现 AutoCloseable |
| M5 | 健康检查端点 | 1 天 | Spring Boot Actuator |
| M7 | 配置增量更新机制 | 1 天 | 实验变更时自动写入版本记录 |

### 严重级别：Low（低）

| 编号 | 问题 | 建议 |
|------|------|------|
| L1 | 实验状态使用 String | 使用 MyBatis-Plus `@EnumValue` |
| L2 | 日志级别在生产环境过高 | 通过 Profile 区分 |
| L4 | ClickHouse 连接池 | 引入 HikariCP |
| L5 | API 限流 | 添加限流拦截器 |

---

## 四、变更摘要

### 新增文件

- `victor-web/.../config/GlobalExceptionHandler.java` — 全局异常处理器
- `.env.example` — 环境变量模板

### 修改文件

| 文件 | 变更 |
|------|------|
| `StatisticsService.java` | 移除所有模拟数据方法，接入真实 ClickHouse 查询 |
| `application.yml` | 密码等敏感信息改为环境变量占位符 |
| `WebConfig.java` | CORS 域名从硬编码改为 `@Value` 配置 |
| `ExperimentController.java` | 移除 `calculateBucketBoundaries` 方法，简化 createExperiment |
| `ExperimentService.java` | 新增 `calculateVariantBucketBoundaries` 和 `checkLayerBucketConflict` |
| `LayerMapper.java` | 新增 `selectByIds` 批量查询 |
| `VariantMapper.java` | 新增 `selectActiveVariantsByExpIds` 批量查询 |
| `BucketingService.java` | 使用批量查询替代 N+1 循环 |
| `StatsEngine.java` | 新增 `getMetricsRepository()` 方法 |
| `ExperimentControllerTest.java` | 补充 `@MockBean ExperimentLifecycleService`，修复验证测试 |

---

## 五、优化前后对比

| 维度 | 优化前 | 优化后 |
|------|--------|--------|
| 统计分析 | 模拟数据（Random(42)） | 真实 ClickHouse 数据 + StatsEngine |
| 安全配置 | 密码明文硬编码 | 环境变量占位符 + .env.example |
| 错误处理 | Spring 默认堆栈泄漏 | 统一 ErrorResponse 格式 |
| Controller 职责 | 包含业务逻辑 | 仅做参数校验和委托 |
| N+1 查询 | 循环 selectById | 批量 IN 查询 |
| 桶冲突检测 | TODO 未实现 | 创建时自动检测 |
| 总体评分 | 3.1 / 5.0 | **3.5 / 5.0** |
