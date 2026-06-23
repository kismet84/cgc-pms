---
name: h2-test-isolation-pagination-table-lock-fix-2026-06-23
description: 修复全量测试套件中 WorkflowQueryServiceTest 分页断言污染 + CtContractPaymentTermServiceTest H2 表锁超时
metadata:
  type: feedback
  tags:
    - backend
    - test
    - h2
    - data-isolation
    - table-lock
    - pagination
    - Db.saveBatch
---

# H2 测试数据污染与表锁修复

## 现象

`mvn test` 全量套件中：
1. `WorkflowQueryServiceTest.getMyTodosReturnsPagedPendingTasks` — `expected: <1> but was: <8>`
2. `WorkflowQueryServiceTest.getMyTodosSecondPageEmpty` — `expected: <1> but was: <8>`
3. `CtContractPaymentTermServiceTest.testBatchSaveReplaceAll` — `Timeout trying to lock table` (H2 `50200-224`)

两个测试单独运行和交叉运行都能通过，只在全量套件（1198 tests, forkCount=1, threadCount=2）中失败。

## 根因

### 失败 1 & 2：跨类数据污染（不是 H2 分页 bug）

`WorkflowQueryServiceTest` 的断言硬编码 `assertEquals(1, page.getTotal())`，假设整张 `wf_task` 表只有自己创建的 1 条 PENDING 记录。全量套件中 `WorkflowEngineIntegrationTest` 等类创建了 7+ 条 `approver_id=1, tenant_id=0, status=PENDING` 的 task 且 `@AfterAll` 的 `DELETE` 可能在 `WorkflowQueryServiceTest` 之后执行（并行线程+类间排序不确定），导致 `total` 实际为 8。

Maven Surefire 配置加剧问题：`forkCount=1 + reuseForks=true + parallel=classes + threadCount=2` → 所有 `@ActiveProfiles("local")` 测试类**共享同一个 H2 内存数据库**。

### 失败 3：Db.saveBatch() 在 H2 并行测试下表锁超时

`CtContractPaymentTermService.batchSave()` 使用 MyBatis-Plus 的 `Db.saveBatch()`，内部使用 JDBC batch executor，在 MySQL 下正常但在 H2 并行测试中与其他测试类的 `ct_contract_payment_term` 写操作竞争，H2 行锁粒度较粗导致 batch flush 超时（`LOCK_TIMEOUT=300000` 虽大但 batch 操作每 1000 条才 flush，中间持有锁太久）。

## 修复

### 1. WorkflowQueryServiceTest — 断言不再假设表为空

- `getMyTodosReturnsPagedPendingTasks`：从 `assertEquals(1, total)` 改为 `assertTrue(total >= 1)` + 按 `submittedTaskId` 精确匹配自己的 task
- `getMyTodosSecondPageEmpty`：改为用大 `pageSize` 先查第 1 页确认自己的 task 在里面，再查第 2 页确认自己的 task **不在**里面
- `getMyDoneSecondPageEmpty`：同样改为按 `instanceId` 精确匹配而非假设 pageSize 分页后第 2 页为空

### 2. CtContractPaymentTermService.batchSave() — Db.saveBatch() → 逐条 insert

将 `Db.saveBatch(newTerms)` 改为 `for (t : newTerms) { ctContractPaymentTermMapper.insert(t); }`，移除 `com.baomidou.mybatisplus.extension.toolkit.Db` import。

付款条款每合同通常 ≤10 条，逐条插入性能损失可忽略，但避免了 JDBC batch executor 在 H2 下的表锁超时。

### 3. CtContractPaymentTermServiceTest — 收紧 @AfterEach 清理

- 移除 `@BeforeEach` 中的 `DELETE FROM ct_contract_payment_term WHERE id > 0`（全局扫表删除是并行锁竞争源）
- `@AfterEach` 改为精确清理 `DELETE ... WHERE contract_id = draftContractId`

## 验证

全量 `mvn test`: 1198 tests, 0 failures, 0 errors, BUILD SUCCESS.
