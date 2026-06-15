# Task-003: 扩展日志敏感数据脱敏规则

## Status: pending

## 用户请求
修复审计报告 P1-22：扩展 Logback 和 OperationLogAspect 的脱敏规则，覆盖更多敏感字段。

## 问题描述

### 1. logback-spring.xml
当前 `%replace` 正则仅覆盖 `password|token|secret|authorization` 四个字段：
```xml
%replace(%msg){'(?i)(password|token|secret|authorization)\s*[:=]\s*[^\s,;&]+', '$1=***MASKED***'}
```
遗漏：`phone`, `email`, `bankAccount`, `creditCode`, `contactPhone` 及中文敏感字段。

### 2. OperationLogAspect.safeArgs()
当前正则仅覆盖 `password|secret|token|accessKey|secretKey`：
```java
s.replaceAll("(?i)(password|secret|token|accessKey|secretKey)=[^,}\\]]+", "$1=***");
```
遗漏：`phone`, `email`, `bankAccount`。

## 目标
扩展两处脱敏正则，覆盖所有敏感字段及中文表达。

## 修改文件

| 文件 | 变更 |
|------|------|
| `backend/src/main/resources/logback-spring.xml` | 扩展 3 处 `%replace` 正则（dev CONSOLE: 行7, prod CONSOLE: 行25, prod FILE: 行40） |
| `backend/src/main/java/com/cgcpms/common/aspect/OperationLogAspect.java` | 扩展 `safeArgs()` 方法中正则（行 73） |

## 修改要求

### logback-spring.xml（3 处相同变更）
将现有的 pattern 中的字段列表从：
```
password|token|secret|authorization
```
扩展为：
```
password|token|secret|authorization|phone|email|bankAccount|creditCode|contactPhone|mobile|idCard|身份证|手机号|银行卡|密码|令牌
```

注意：中文敏感字段在 `%replace` 的 `%msg` 中按字面匹配即可，它们在正则中是普通字符。

### OperationLogAspect.safeArgs()（行 73）
将：
```java
s.replaceAll("(?i)(password|secret|token|accessKey|secretKey)=[^,}\\]]+", "$1=***");
```
扩展为：
```java
s.replaceAll("(?i)(password|secret|token|accessKey|secretKey|phone|email|bankAccount|creditCode|contactPhone|mobile|idCard|身份证|手机号|银行卡|密码|令牌)=[^,}\\]]+", "$1=***");
```

### SensitiveDataMaskingTest.java
如果存在测试文件 `backend/src/test/java/com/cgcpms/common/logging/SensitiveDataMaskingTest.java`，请检查是否需要更新测试用例以覆盖新增字段。若不存在则跳过。

## 约束
- 不修改日志格式、appender 配置、日志级别等其他 logback 配置
- 不修改 safeArgs() 方法外的其他代码
- 保持现有正则匹配逻辑不变（仅扩展字段列表）
- 保持 XML/Java 文件缩进和格式一致
- 如果 `SensitiveDataMaskingTest.java` 中存在针对旧 regex 的硬编码断言，更新它们

## 验收标准
1. logback-spring.xml 中 3 处 `%replace` 的字段列表均已扩展
2. OperationLogAspect.safeArgs() 中正则已扩展
3. `mvnw compile` 编译通过
4. 如有 `SensitiveDataMaskingTest`，`mvnw test -Dtest=SensitiveDataMaskingTest` 通过
