# `fix/audit-issues-20260705` 分支复核报告

## 结论

仍然**不通过**，当前**不可收口**。

## 已修复的点

1. 用户态缓存问题已回正。
   - `frontend-admin/src/stores/user.ts` 现在重新持久化 `roles` 和 `permissions`，不会再把刷新后的登录态打成“有用户名但无权限”的半残状态。

2. 合同乐观锁链路已补齐。
   - `CtContract` 新增 `@Version`，`CtContractService` 的更新和提交审批都已经带版本条件，并在更新后递增版本号。

3. 成本列表页恢复了原有筛选能力的主要部分。
   - `costType` 和 `sourceType` 已回到页面和查询参数中。

## 仍然阻塞的点

### 1. 批量标记预警已读绕过了预警访问域校验

- 严重级别：高
- 位置：
  - [`backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`](../../../../backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java):894
  - [`backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`](../../../../backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java):906
  - [`backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`](../../../../backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java):916
  - [`backend/src/main/java/com/cgcpms/alert/auth/AlertAccessScopeResolver.java`](../../../../backend/src/main/java/com/cgcpms/alert/auth/AlertAccessScopeResolver.java):92
- 证据：
  - 单条 `markRead()` 仍然调用 `assertAlertAccess()`。
  - 但 `batchMarkRead()` 现在只做了 `selectBatchIds()` + `tenantId` 过滤，然后直接批量更新。
  - 这条路径没有再调用 `assertAlertAccess()`。
- 影响：
  - 非管理员用户可能对本不该访问的预警执行批量已读。
  - 这属于权限/数据面回归，不是展示级问题。
- 最小修复建议：
  - 批量接口不要直接按租户批量更新，改回逐条复用 `markRead()` 的访问校验，或者在批量前逐条执行 `assertAlertAccess()`。

### 2. 成本列表页仍丢失 `costSubjectId` 的可见筛选入口

- 严重级别：中
- 位置：
  - [`frontend-admin/src/pages/cost/ledger.vue`](../../../../frontend-admin/src/pages/cost/ledger.vue):48
  - [`frontend-admin/src/pages/cost/ledger.vue`](../../../../frontend-admin/src/pages/cost/ledger.vue):132
  - [`frontend-admin/src/pages/cost/ledger.vue`](../../../../frontend-admin/src/pages/cost/ledger.vue):161
- 证据：
  - 页面状态和查询参数里已经恢复了 `costSubjectId`。
  - 但当前页面模板里没有对应的可见筛选控件，用户无法在页面上主动选择这个条件。
- 影响：
  - 相比后端契约，前端仍少一个可用过滤入口。
  - 如果原本这就是产品能力的一部分，这属于功能残缺；如果是有意收缩，需要同步改需求和测试口径。
- 最小修复建议：
  - 如果成本科目筛选仍在范围内，把控件恢复出来。
  - 如果不在范围内，删除后端和测试里对它的依赖，别保留半吊子参数。

### 3. `DevAuthController` 的重定向白名单仍不完整

- 严重级别：低
- 位置：
  - [`backend/src/main/java/com/cgcpms/auth/controller/DevAuthController.java`](../../../../backend/src/main/java/com/cgcpms/auth/controller/DevAuthController.java):76
  - [`backend/src/main/java/com/cgcpms/auth/controller/DevAuthController.java`](../../../../backend/src/main/java/com/cgcpms/auth/controller/DevAuthController.java):97
- 证据：
  - 白名单覆盖了很多前端前缀，但没有覆盖全部现有入口。
  - 当前路由里仍有 `/purchase`、`/org`、`/cost-target` 等入口，重定向可能被降到 `/`。
- 影响：
  - 这是 dev/local 登录跳转可用性问题，不是生产主链路问题。
- 建议：
  - 按实际前端路由补全白名单。

## 当前裁决

- 结论：**仍不可收口**
- 阻塞项：
  - 批量预警已读绕过访问域校验
- 非阻塞项：
  - 成本科目筛选入口未恢复
  - Dev 登录重定向白名单不完整

