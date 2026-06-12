# WorkflowEngineIntegrationTest H2 暂缓问题分析报告

报告日期：2026-06-12

## 1. 背景

`WorkflowEngineIntegrationTest` 当前有 2 个用例在 H2 环境暂未通过：

- `test09_idempotencyBlocksDuplicate`：幂等约束校验。
- `test15_lifecycleNotifications`：通知 `tenantId` 校验。

已知现象：MySQL 环境正常，H2 环境存在失败。本文分析结论聚焦 H2 与 MySQL 行为差异、测试用例查询条件、测试数据隔离三个方向。

## 2. 结论摘要

这两个暂缓项更符合“测试环境差异 / 测试用例隔离不足”，当前证据不支持判定为 MySQL 正式环境下的工作流业务逻辑缺陷。

- `test09` 根因明确：H2 的 `wf_idempotency` 表缺少 MySQL 中存在的唯一约束，导致重复幂等键插入不会触发 `DuplicateKeyException`。
- `test15` 更像测试查询条件过宽导致的 H2 数据污染或不确定命中：通知写入链路会显式传入 `tenantId`，但转办、加签通知的断言没有稳定限定本次用例的数据范围。

建议短期继续以 MySQL 集成结果作为准入依据，同时修复 H2 schema 和 test15 查询条件，恢复 H2 用例的有效性。

## 3. test09：幂等约束暂未通过分析

### 3.1 用例意图

`test09_idempotencyBlocksDuplicate` 在第二、第三个流程实例中复用同一个 `idempotencyKey`：

- 第一次使用 `idemKey2` 审批 `task2`，预期成功。
- 第二次使用同一个 `idemKey2` 审批另一个仍处于 `PENDING` 状态的 `task3`，预期抛异常。

相关测试位置：

- `backend/src/test/java/com/cgcpms/workflow/WorkflowEngineIntegrationTest.java:420`
- `backend/src/test/java/com/cgcpms/workflow/WorkflowEngineIntegrationTest.java:436`

### 3.2 业务实现

`WorkflowEngine.checkIdempotency` 使用 insert-first 策略：

- 先向 `wf_idempotency` 插入当前 `(tenantId, userId, idempotencyKey)`。
- 依赖数据库唯一约束原子化拦截重复请求。
- 捕获 `DuplicateKeyException` 后转为业务异常 `DUPLICATE_REQUEST`。

相关代码位置：

- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java:713`

### 3.3 MySQL 与 H2 schema 差异

MySQL Flyway 迁移中，`wf_idempotency` 存在唯一约束：

```sql
UNIQUE KEY uk_wf_idempotency (tenant_id, user_id, idempotency_key)
```

相关文件：

- `backend/src/main/resources/db/migration/V3__init_workflow_tables.sql:197`
- `backend/src/main/resources/db/migration/V3__init_workflow_tables.sql:209`

H2 schema 中，`wf_idempotency` 仅定义了主键：

```sql
PRIMARY KEY (id)
```

未定义 `(tenant_id, user_id, idempotency_key)` 唯一约束。

相关文件：

- `backend/src/main/resources/db/h2/schema.sql:324`
- `backend/src/main/resources/db/h2/schema.sql:335`

### 3.4 根因判断

`test09` 在 H2 下失败的直接原因是 H2 schema 漂移。业务代码依赖数据库唯一约束判断重复幂等键，MySQL 具备该约束，H2 不具备，因此 H2 下第二次插入同一幂等键不会抛出重复键异常。

这属于测试数据库结构不等价，不是 MySQL 环境下的幂等逻辑失效。

## 4. test15：通知 tenantId 校验暂未通过分析

### 4.1 用例意图

`test15_lifecycleNotifications` 使用 `tenantId = 777L` 创建多种工作流生命周期场景，并校验相关通知也应写入 tenant `777L`：

- 提交通知。
- 审批通过通知。
- 驳回通知。
- 撤回通知。
- 转办通知。
- 加签通知。

相关测试位置：

- `backend/src/test/java/com/cgcpms/workflow/WorkflowEngineIntegrationTest.java:627`

### 4.2 通知写入链路

`NotificationService.create` 会直接把入参 `tenantId` 写入 `SysNotification`：

```java
notification.setTenantId(tenantId);
```

相关代码位置：

- `backend/src/main/java/com/cgcpms/notification/service/NotificationService.java:50`
- `backend/src/main/java/com/cgcpms/notification/service/NotificationService.java:53`

转办、加签通知中，`WorkflowEngine` 会从工作流实例读取租户：

- 转办通知使用 `instance.getTenantId()`，必要时重新加载实例。
- 加签通知重新查询实例后使用 `instanceForNotify.getTenantId()`。

相关代码位置：

- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java:410`
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java:417`
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java:491`
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java:496`

从代码链路看，当前没有发现通知 tenantId 被 `UserContext` 中的默认租户覆盖的证据。

### 4.3 测试查询条件问题

`test15` 前半段提交、通过、驳回、撤回通知查询使用了 `bizId` 和 `bizType` 限定，数据范围相对稳定。

但转办通知查询只限定：

- `bizType = WORKFLOW`
- `userId = USER_MANAGER`
- `title like 转办了一个审批给你`

然后直接取 `transferNotifs.get(0)` 断言 tenantId。

相关测试位置：

- `backend/src/test/java/com/cgcpms/workflow/WorkflowEngineIntegrationTest.java:725`
- `backend/src/test/java/com/cgcpms/workflow/WorkflowEngineIntegrationTest.java:731`

加签通知查询只限定：

- `bizType = WORKFLOW`
- `title like 邀请你加签审批`

然后遍历所有匹配通知，要求每条通知都是 tenant `777L` 且用户属于本次加签对象。

相关测试位置：

- `backend/src/test/java/com/cgcpms/workflow/WorkflowEngineIntegrationTest.java:748`
- `backend/src/test/java/com/cgcpms/workflow/WorkflowEngineIntegrationTest.java:753`

该查询范围过宽。如果 H2 中存在历史测试、同类测试或前序用例留下的同标题通知，就可能命中非本次用例数据，导致 tenantId 断言失败。

### 4.4 bizId 语义差异

转办、加签通知创建时传入的 `bizId` 是 `task.getBusinessId()`，即工作流业务 ID，而不是工作流实例 ID：

- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java:420`
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java:499`

而 test15 前半段其他通知多使用实例 ID 作为 `bizId` 查询。这意味着如果修复 test15 查询条件，需要按具体通知创建逻辑选择正确的 `bizId`：

- 提交、通过、驳回、撤回：当前实现使用实例 ID。
- 转办、加签：当前实现使用业务 ID，即 `RUN_ID + 18`、`RUN_ID + 19`。

这也是 test15 后半段没有简单追加实例 ID 查询的原因之一。

### 4.5 清理逻辑观察

测试类 `cleanupTestData` 会删除 `sys_notification WHERE biz_id BETWEEN startBid AND endBid`：

- `backend/src/test/java/com/cgcpms/workflow/WorkflowEngineIntegrationTest.java:841`

这可以覆盖转办、加签这类以业务 ID 作为 `bizId` 的通知，但无法覆盖提交、通过、驳回、撤回这类以实例 ID 作为 `bizId` 的通知。若 H2 数据库生命周期跨测试上下文或跨运行残留，这类通知存在污染后续断言的可能。

## 5. 风险评估

### 5.1 对正式 MySQL 环境的风险

较低。

- 幂等逻辑在 MySQL 中依赖的唯一约束存在，且与代码策略匹配。
- 通知 tenantId 写入链路显式传参，未发现 MySQL 环境下 tenantId 被覆盖的证据。

### 5.2 对测试质量的风险

中等。

- H2 schema 缺失唯一约束会导致 H2 无法覆盖真实数据库约束行为。
- test15 查询条件过宽，会造成环境相关的误报。
- 通知 `bizId` 在不同动作中语义不完全一致，增加测试维护成本。

### 5.3 对后续回归的风险

中等。

如果继续暂缓但不修复，后续 H2 回归结果会持续包含已知噪声，降低 CI 对真实问题的提示价值。

## 6. 建议处理方案

### 6.1 短期修复

1. 补齐 H2 `wf_idempotency` 约束。

建议在 `backend/src/main/resources/db/h2/schema.sql` 中为 `wf_idempotency` 增加：

```sql
UNIQUE (tenant_id, user_id, idempotency_key)
```

必要时同时补齐：

```sql
CREATE INDEX IF NOT EXISTS idx_wf_idempotency_expired ON wf_idempotency (expired_at);
```

2. 收紧 test15 转办通知查询。

建议增加：

- `tenantId = 777L`
- `bizId = RUN_ID + 18`
- `userId = USER_MANAGER`

避免使用未排序的 `get(0)` 命中历史数据。

3. 收紧 test15 加签通知查询。

建议增加：

- `tenantId = 777L`
- `bizId = RUN_ID + 19`
- `userId IN (USER_BIZ, USER_COST)`

并只断言本次用例生成的通知数量与接收人。

4. 优化通知清理逻辑。

建议同时清理两类 `bizId`：

- 业务 ID 范围：`RUN_ID + 1` 到 `RUN_ID + 21`。
- 本次业务 ID 对应的 workflow instance ID 集合。

这样可以覆盖当前 submit/approve/reject/withdraw 与 transfer/addSign 对 `bizId` 使用不一致的问题。

### 6.2 中期改进

1. 尽量从 Flyway 迁移生成或校验 H2 schema，减少 H2 与 MySQL schema 漂移。
2. 对 `sys_notification.biz_id` 的语义做统一约定：工作流通知统一使用 workflow instance ID 或统一使用业务 ID，并在代码和测试中保持一致。
3. 对 `WorkflowEngineIntegrationTest` 引入更强的数据隔离策略，例如每个测试独立租户、独立业务 ID 前缀、或每个测试后按 instance/business 双路径清理。

## 7. 暂缓建议

建议接受当前 H2 暂缓状态，但需要在缺陷跟踪中标记为“测试环境一致性问题”，而不是关闭。

推荐优先级：

- P1：修复 H2 `wf_idempotency` 唯一约束，恢复 `test09` 有效性。
- P2：收紧 `test15` 查询条件与清理逻辑，消除通知 tenantId 误报。
- P3：统一工作流通知 `bizId` 语义，降低长期维护成本。

在上述修复完成前，MySQL 集成测试通过可以作为本轮业务逻辑验收依据，但 H2 结果不应作为阻断发布的唯一依据。
