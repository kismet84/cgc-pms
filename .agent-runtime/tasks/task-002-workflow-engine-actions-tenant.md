# Task-002: WorkflowEngine.getAvailableActions 移除不安全重载

## Status: pending

## 用户请求
修复审计报告 P1-10 相关问题：`WorkflowEngine.getAvailableActions(Long instanceId, Long userId)` 重载传 `null` 作为 `tenantId`，导致跳过租户过滤。

## 问题描述
`backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java` 行 88-90：
```java
public List<String> getAvailableActions(Long instanceId, Long userId) {
    return getAvailableActions(null, instanceId, userId);
}
```
此重载传 `null` 给 tenantId，在另一个重载中（行 95-97）：
```java
if (tenantId != null) {
    instanceWrapper.eq(WfInstance::getTenantId, tenantId);
}
```
会跳过租户过滤。

## 调用分析
- 生产代码中 **唯一** 调用者：`WorkflowQueryService.java:186` 使用 `getAvailableActions(tenantId, instanceId, currentUserId)` — 安全
- 测试代码中的调用者：`WorkflowEngineIntegrationTest.java:459, 465` 使用 `getAvailableActions(instance.getId(), USER_ADMIN)` — 使用不安全重载

## 目标
移除不安全的 `getAvailableActions(Long, Long)` 重载，将所有调用者迁移到安全版本。

## 修改范围
- **IN SCOPE**: 
  - `WorkflowEngine.java` 行 88-90：删除不安全重载
  - `WorkflowEngineIntegrationTest.java` 行 459, 465：修改调用为安全版本
- **OUT OF SCOPE**: 其他方法、其他文件

## 修改要求
1. 删除 `WorkflowEngine.java` 行 88-90 的 `getAvailableActions(Long instanceId, Long userId)` 方法
2. 修改 `WorkflowEngineIntegrationTest.java` 行 459、465：
   - 改为 `workflowEngine.getAvailableActions(instance.getTenantId(), instance.getId(), USER_ADMIN)`
   - 需要添加获取 `tenantId` 的代码（检查测试中可用的 tenantId 常量如 `DEFAULT_TENANT_ID` 或 `TENANT_ID`）

## 约束
- 不修改其他生产代码
- 不修改其他测试方法
- 测试必须编译通过
- 遵循现有代码风格

## 验收标准
1. `WorkflowEngine.java` 中不存在两个参数版本的重载
2. `WorkflowEngineIntegrationTest.java` 编译通过，使用三参数版本
3. 搜索确认无其他代码调用两个参数版本

## 验证方式
- 运行 `mvnw compile` 确认生产代码编译通过
- 运行 `mvnw test-compile` 确认测试代码编译通过
- 搜索确认 `getAvailableActions(instanceId, userId)` 无其他调用
