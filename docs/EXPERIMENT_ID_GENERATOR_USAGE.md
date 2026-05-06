# 实验ID生成器使用说明

## 📋 功能概述

`ExperimentIdGenerator` 是一个用于生成实验ID的工具类，采用简洁的日期+随机数格式，总长度固定为7位。

---

## 🎯 ID格式规范

### 格式结构

```
[年份最后一位][月份2位][日期2位][随机数3位] = 7位
```

### 示例

| 日期 | 生成的ID示例 | 说明 |
|------|-------------|------|
| 2026-05-06 | `60506123` | 6(年) + 05(月) + 06(日) + 123(随机) |
| 2026-12-25 | `61225987` | 6(年) + 12(月) + 25(日) + 987(随机) |
| 2027-01-01 | `70101456` | 7(年) + 01(月) + 01(日) + 456(随机) |

---

## 💡 使用方式

### 1. 基本用法

```java
import com.gateflow.victor.common.util.ExperimentIdGenerator;

// 生成当前日期的实验ID
String experimentId = ExperimentIdGenerator.generate();
// 输出: 60506123 (示例)
```

### 2. 指定日期

```java
import java.time.LocalDate;

// 生成指定日期的实验ID
LocalDate date = LocalDate.of(2026, 12, 25);
String experimentId = ExperimentIdGenerator.generate(date);
// 输出: 61225XXX
```

### 3. 使用时间戳（高并发场景）

```java
// 使用纳秒时间戳作为随机数种子，提高并发生成时的唯一性
String experimentId = ExperimentIdGenerator.generateWithTimestamp();
```

### 4. 批量生成

```java
// 批量生成10个实验ID
String[] ids = ExperimentIdGenerator.generateBatch(10);
```

---

## 🔍 辅助方法

### 验证ID格式

```java
boolean isValid = ExperimentIdGenerator.isValid("60506123");
// 返回: true

boolean isValid2 = ExperimentIdGenerator.isValid("123456");
// 返回: false (长度不对)
```

### 提取日期部分

```java
String datePart = ExperimentIdGenerator.getDatePart("60506123");
// 返回: "60506"
```

### 提取随机数部分

```java
String randomPart = ExperimentIdGenerator.getRandomPart("60506123");
// 返回: "123"
```

### 从ID中提取完整日期

```java
String fullDate = ExperimentIdGenerator.extractDate("60506123");
// 返回: "2026-05-06"
```

---

## 📊 唯一性分析

### 每日容量

- **随机数范围**: 000-999 (3位)
- **每日可生成**: 1,000个唯一ID
- **唯一性**: 同一天内100%唯一（如果随机数不重复）

### 实际场景

```java
// 模拟一天内生成100个ID的重复率测试
Set<String> ids = new HashSet<>();
for (int i = 0; i < 100; i++) {
    ids.add(ExperimentIdGenerator.generate());
}

// 100次生成通常产生95-100个唯一ID
// 重复概率: ~0-5%
```

### 提高唯一性的方法

1. **使用时间戳模式** (推荐高并发场景):
```java
String id = ExperimentIdGenerator.generateWithTimestamp();
```

2. **结合数据库唯一约束**:
```sql
ALTER TABLE victor_experiment 
ADD UNIQUE KEY uk_exp_id (exp_id);
```

3. **重试机制**:
```java
public String generateUniqueExpId() {
    int maxRetries = 10;
    for (int i = 0; i < maxRetries; i++) {
        String expId = ExperimentIdGenerator.generate();
        if (!experimentMapper.existsByExpId(expId)) {
            return expId;
        }
    }
    throw new RuntimeException("Failed to generate unique experiment ID");
}
```

---

## 🔧 在Service中的集成

### ExperimentService自动使用

```java
@Service
public class ExperimentService {
    
    @Transactional
    public Experiment createExperiment(Experiment experiment, List<Variant> variants) {
        // ... 验证逻辑 ...
        
        // 自动生成实验ID
        String expId = ExperimentIdGenerator.generate();
        experiment.setExpId(expId);
        
        // ... 插入数据库 ...
        
        log.info("Created experiment with ID: {}", expId);
        return experiment;
    }
}
```

---

## ⚠️ 注意事项

### 1. 长度限制

- **固定7位**：不可更改
- **全部为数字**：不包含字母或特殊字符
- **前导零**：日期部分会有前导零（如 `60101XXX` 表示1月1日）

### 2. 年份推断

由于只保存年份最后一位，从ID反推日期时会有10年的歧义：

```java
// ID: 60506123
// 可能是: 2026-05-06 或 2016-05-06 或 2036-05-06

// extractDate() 方法会自动推断为当前年份前后10年范围内
String date = ExperimentIdGenerator.extractDate("60506123");
// 假设当前是2026年，返回: "2026-05-06"
```

### 3. 并发场景

如果在高并发场景下使用，建议使用 `generateWithTimestamp()` 方法，或在数据库层面添加唯一约束和重试机制。

### 4. 跨日边界

```java
// 23:59:59 调用 → 可能生成: 60506XXX
// 00:00:01 调用 → 可能生成: 60507XXX
// 日期部分会变化
```

---

## 🧪 测试用例

### 运行测试

```bash
mvn test -Dtest=ExperimentIdGeneratorTest
```

### 测试覆盖

- ✅ 基本生成功能
- ✅ 长度验证（固定7位）
- ✅ 日期部分正确性
- ✅ 随机数范围验证（000-999）
- ✅ 唯一性测试（批量生成）
- ✅ 并发场景测试
- ✅ 边界情况（月初、月末、闰年）
- ✅ ID格式验证
- ✅ 日期提取功能

---

## 📈 扩展建议

### 1. 如果需要更高唯一性

可以调整为：`年2位 + 月日4位 + 序号3位 = 9位`

```java
// 例如: 260506001
// 优点: 序号保证唯一
// 缺点: ID长度增加
```

### 2. 如果需要包含业务前缀

```java
// 例如: EXP-60506123
// 需要在生成后添加前缀
String id = "EXP-" + ExperimentIdGenerator.generate();
```

### 3. 如果需要数据库自增ID作为后备

```java
// 使用数据库自增ID作为主键
// exp_id作为业务ID（人类可读）
// 两者结合使用
```

---

## 📝 总结

| 特性 | 说明 |
|------|------|
| **格式** | 年(1位) + 月(2位) + 日(2位) + 随机数(3位) |
| **长度** | 固定7位数字 |
| **每日容量** | 1,000个ID |
| **适用场景** | 中小规模实验平台，每日创建实验<500个 |
| **优点** | 简洁、可读性强、包含日期信息 |
| **限制** | 同一天内大量创建时可能重复 |

**推荐使用场景**：
- ✅ 实验管理系统的业务ID
- ✅ 需要人类可读的ID
- ✅ 每日实验创建量<500的场景

**不推荐使用场景**：
- ❌ 高并发生成（>1000/天）
- ❌ 需要全局唯一性保证
- ❌ 需要包含更多信息的场景
