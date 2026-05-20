# 数据库变更日志

## V2.1 - 修改 params 字段类型

**日期**: 2026-05-06  
**类型**: 字段类型修改  
**影响表**: victor_variant

### 变更内容

```sql
-- 修改 params 字段从 JSON 类型改为 VARCHAR(64)
ALTER TABLE victor_variant 
MODIFY COLUMN params VARCHAR(64) DEFAULT NULL COMMENT '版本参数';
```

### 变更原因

1. **灵活性需求**: 版本参数不一定是 JSON 格式，可能是简单的键值对、配置标识符等
2. **简化存储**: 对于简单的参数配置，VARCHAR 更轻量
3. **查询友好**: VARCHAR 类型在某些场景下更容易进行字符串匹配

### 影响分析

| 项目 | 变更前后 | 影响 |
|------|---------|------|
| **字段类型** | JSON → VARCHAR(64) | 支持任意字符串格式 |
| **最大长度** | 无限制 → 64字符 | 需要注意长度限制 |
| **数据验证** | JSON格式验证 → 无格式限制 | 更灵活但需应用层验证 |
| **现有数据** | 2条记录，最长24字符 | ✅ 无数据丢失风险 |

### 现有数据检查

```sql
SELECT id, variant_key, params, LENGTH(params) AS param_length 
FROM victor_variant;

-- 结果:
-- id=5, control,     "{}",                         2字符  ✅
-- id=6, treatment,   "{"button_color": "blue"}",   24字符 ✅
```

### Java 实体类同步

**文件**: `Variant.java`

```java
/**
 * 版本参数 (VARCHAR(64)，支持任意字符串格式)
 */
private String params;
```

**变更**: 注释从 `版本参数 (JSON)` 更新为 `版本参数 (VARCHAR(64)，支持任意字符串格式)`

### 使用示例

#### 之前（JSON格式）
```java
variant.setParams("{\"button_color\":\"blue\",\"font_size\":16}");
```

#### 现在（灵活格式）
```java
// 方式1: 简单字符串
variant.setParams("blue_button_v2");

// 方式2: 键值对
variant.setParams("color=blue;size=large");

// 方式3: JSON（仍然支持，只要长度≤64）
variant.setParams("{\"color\":\"blue\"}");

// 方式4: 配置标识符
variant.setParams("config_premium_2026");
```

### 注意事项

1. **长度限制**: 参数长度不能超过64字符
2. **应用层验证**: 如果使用JSON格式，需在应用层验证JSON有效性
3. **向后兼容**: 现有的JSON数据仍然可用，无需迁移
4. **索引考虑**: VARCHAR(64) 可以创建索引，如果需要按参数查询可添加索引

### 回滚方案

如果需要恢复为 JSON 类型：

```sql
ALTER TABLE victor_variant 
MODIFY COLUMN params JSON DEFAULT NULL COMMENT '版本参数';
```

### 相关文档

- [V2 迁移脚本](./victor-infrastructure/src/main/resources/db/migration/V2__add_variant_versioning.sql)
- [Variant 实体类](../victor-domain/src/main/java/com/gateflow/victor/domain/entity/Variant.java)

---

## V2 - 添加版本控制

**日期**: 2026-05-06  
**类型**: 新增字段和索引  
**影响表**: victor_variant

### 变更内容

1. 新增 `version` 字段 (VARCHAR(32))
2. 新增 `is_active` 字段 (BOOLEAN)
3. 删除旧索引 `uk_exp_variant`
4. 新增索引 `uk_exp_version_variant`、`idx_exp_version`、`idx_exp_active`

详见: [V2__add_variant_versioning.sql](./victor-infrastructure/src/main/resources/db/migration/V2__add_variant_versioning.sql)
