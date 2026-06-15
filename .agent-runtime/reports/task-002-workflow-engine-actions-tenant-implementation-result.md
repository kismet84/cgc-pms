# Task-002 Implementation Report: WorkflowEngine Unsafe Overload Removal

## Status: ✅ Complete

## Summary
移除了 `WorkflowEngine.getAvailableActions(Long instanceId, Long userId)` 不安全重载（传 null tenantId），所有调用者迁移到安全的三参数版本。

## Changed Files
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java` — 删除不安全重载
- `backend/src/test/java/com/cgcpms/workflow/WorkflowEngineIntegrationTest.java` — 更新测试调用

## Specific Changes
1. 删除 `WorkflowEngine.java` 行 88-90 的无 tenantId 重载方法
2. 更新 `WorkflowEngineIntegrationTest.java` 行 459, 465：`getAvailableActions(instance.getTenantId(), instance.getId(), USER_ADMIN)`

## Verification
- `mvnw compile` — 编译通过
- `mvnw test -Dtest=WorkflowEngineIntegrationTest#test10_availableActionsByStatus` — 通过
- `mvnw test` — 227/227 通过，0 失败
- 搜索确认：无其他代码调用两参数版本
