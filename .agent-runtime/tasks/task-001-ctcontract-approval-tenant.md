# Task-001: CtContractService.getApprovalRecords 添加租户隔离

## Status: pending

## 用户请求
修复审计报告 P1-10 相关问题：`CtContractService.getApprovalRecords()` 查询 `wf_instance` 和 `wf_record` 时缺少 `tenantId` 过滤。

## 问题描述
`backend/src/main/java/com/cgcpms/contract/service/CtContractService.java` 的 `getApprovalRecords(Long contractId)` 方法（行 213-225）中：
- 查询 `wf_instance` 时用了 `businessType + businessId` 定位，未加 `.eq(WfInstance::getTenantId, tenantId)`
- 查询 `wf_record` 时只用了 `instanceId`，未加 `.eq(WfRecord::getTenantId, tenantId)`

## 目标
在该方法中添加租户隔离过滤，与其他 Service 保持一致。

## 修改范围
- **IN SCOPE**: `CtContractService.getApprovalRecords()` 方法（约行 213-225）
- **OUT OF SCOPE**: 其他方法、其他文件

## 参考模式
项目中所有其他 Service 的租户隔离实现均使用：
```java
wrapper.eq(Entity::getTenantId, UserContext.getCurrentTenantId())
```
参考文件：`CtContractService.java` 行 60（getPage 方法中已有此模式）。

## 修改要求
1. 在 `getApprovalRecords` 方法中获取 `tenantId`：`Long tenantId = UserContext.getCurrentTenantId();`
2. 在 `instQw`（WfInstance 查询）中添加：`.eq(WfInstance::getTenantId, tenantId)`
3. 在 `recQw`（WfRecord 查询）中添加：`.eq(WfRecord::getTenantId, tenantId)`
4. 删除或更新行 209-211 的注释（声称"租户隔离由 WorkflowQueryService 处理"已不准确）

## 约束
- 不修改其他方法
- 不修改任何测试文件
- 不引入新的 import（UserContext 应该已在 import 中）
- 遵循现有代码风格

## 验收标准
1. `getApprovalRecords` 方法编译通过
2. 两个 LambdaQueryWrapper 均包含 `.eq(Entity::getTenantId, tenantId)`
3. 注释已更新或删除

## 验证方式
- 运行 `mvnw compile` 确认编译通过
- 检查方法签名和查询条件
