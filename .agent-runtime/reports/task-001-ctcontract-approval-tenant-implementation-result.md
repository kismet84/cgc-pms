# Task-001 Implementation Report: CtContractService Tenant Isolation

## Status: ✅ Complete

## Summary
在 `CtContractService.getApprovalRecords()` 方法中添加了租户隔离过滤，与其他 Service 保持一致。

## Changed Files
- `backend/src/main/java/com/cgcpms/contract/service/CtContractService.java`

## Specific Changes
1. 添加 `Long tenantId = UserContext.getCurrentTenantId();` 获取当前租户 ID
2. `instQw` (WfInstance 查询) 添加 `.eq(WfInstance::getTenantId, tenantId)`
3. `recQw` (WfRecord 查询) 添加 `.eq(WfRecord::getTenantId, tenantId)`
4. 更新注释从"租户隔离由 WorkflowQueryService 处理"改为"含租户隔离"

## Verification
- `mvnw compile` — 编译通过
- `mvnw test` — 227/227 通过，0 失败
