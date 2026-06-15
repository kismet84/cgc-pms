# Task-003 Implementation Report: Log Sensitive Data Masking Expansion

## Status: ✅ Complete

## Summary
扩展了 Logback `%replace` 脱敏正则和 `OperationLogAspect.safeArgs()` 正则，覆盖 phone、email、bankAccount、creditCode、contactPhone、mobile、idCard 及中文敏感字段。

## Changed Files
- `backend/src/main/resources/logback-spring.xml` — 3 处 `%replace` 正则扩展
- `backend/src/main/java/com/cgcpms/common/aspect/OperationLogAspect.java` — `safeArgs()` 正则扩展
- `backend/src/test/java/com/cgcpms/common/logging/SensitiveDataMaskingTest.java` — 同步更新测试正则和用例

## Specific Changes

### logback-spring.xml（3 处相同变更）
从 `password|token|secret|authorization` 扩展为：`password|token|secret|authorization|phone|email|bankAccount|creditCode|contactPhone|mobile|idCard|身份证|手机号|银行卡|密码|令牌`

### OperationLogAspect.safeArgs()
同样扩展字段列表 + 中文：`password|secret|token|accessKey|secretKey|phone|email|bankAccount|creditCode|contactPhone|mobile|idCard|身份证|手机号|银行卡|密码|令牌`

### SensitiveDataMaskingTest.java
- 更新 `SENSITIVE` Pattern 和 `REPLACE_PATTERN` 常量
- 修复 `leavesNonSensitiveKeyValueUntouched` 测试（`email` 现已为脱敏字段，改用 `department`）

## Verification
- `mvnw compile` — 编译通过
- `mvnw test -Dtest=SensitiveDataMaskingTest` — 全部通过
- `mvnw test` — 227/227 通过，0 失败
