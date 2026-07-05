# `fix/audit-issues-20260705` 分支第三轮复核报告

## 结论

**通过，非阻塞，可收口。**

## 本轮确认已闭环

1. 用户态缓存回归已修复。
   - [`frontend-admin/src/stores/user.ts`](D:/projects-test/cgc-pms/frontend-admin/src/stores/user.ts) 已恢复 `roles` / `permissions` 持久化。
   - 路由守卫和 `v-permission` 的会话恢复链路重新对齐。

2. 合同乐观锁链路已补全。
   - [`backend/src/main/java/com/cgcpms/contract/entity/CtContract.java`](D:/projects-test/cgc-pms/backend/src/main/java/com/cgcpms/contract/entity/CtContract.java) 新增 `@Version`。
   - [`backend/src/main/java/com/cgcpms/contract/service/CtContractService.java`](D:/projects-test/cgc-pms/backend/src/main/java/com/cgcpms/contract/service/CtContractService.java) 的更新、提交和复合保存都已带版本条件。

3. 成本列表筛选能力已恢复。
   - [`frontend-admin/src/pages/cost/ledger.vue`](D:/projects-test/cgc-pms/frontend-admin/src/pages/cost/ledger.vue) 重新包含 `costSubjectId`、`costType`、`sourceType` 的筛选状态和查询参数。
   - 页面也恢复了成本科目、成本类型、来源类型的可见筛选入口。

4. 批量预警已读的访问域校验已恢复。
   - [`backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`](D:/projects-test/cgc-pms/backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java) 的批量已读流程现在逐条执行 `assertAlertAccess()`，不再只按租户做裸批量更新。

5. Dev 登录重定向白名单已补齐主要入口。
   - [`backend/src/main/java/com/cgcpms/auth/controller/DevAuthController.java`](D:/projects-test/cgc-pms/backend/src/main/java/com/cgcpms/auth/controller/DevAuthController.java) 现在覆盖了 `cost-target`、`purchase`、`org` 等此前缺失的一级路由前缀。

## 仍然存在的非阻塞风险

### 1. Dev 登录白名单不是完全穷尽

- 位置：
  - [`backend/src/main/java/com/cgcpms/auth/controller/DevAuthController.java`](D:/projects-test/cgc-pms/backend/src/main/java/com/cgcpms/auth/controller/DevAuthController.java):73-96
  - [`frontend-admin/src/router/index.ts`](D:/projects-test/cgc-pms/frontend-admin/src/router/index.ts):392-398
- 说明：
  - 当前白名单已覆盖主干入口，但还不是“自动跟随路由表”的穷举式实现。
  - `settings`、`help` 这类低频页面仍可能回退到 `/`。
- 影响：
  - 仅影响 dev/local 跳转体验，不影响生产主链路。

### 2. 成本科目下拉当前使用全量列表

- 位置：
  - [`frontend-admin/src/pages/cost/ledger.vue`](D:/projects-test/cgc-pms/frontend-admin/src/pages/cost/ledger.vue):47
  - [`backend/src/main/java/com/cgcpms/cost/controller/CostSubjectController.java`](D:/projects-test/cgc-pms/backend/src/main/java/com/cgcpms/cost/controller/CostSubjectController.java):24-29
  - [`backend/src/main/java/com/cgcpms/cost/service/CostSubjectService.java`](D:/projects-test/cgc-pms/backend/src/main/java/com/cgcpms/cost/service/CostSubjectService.java):55-66
- 说明：
  - 页面调用的是 `getCostSubjectList()`，没有传 `category`。
  - 后端接口默认返回全部成本科目，而不是只返回 `COST` 类别。
- 影响：
  - 如果产品口径只希望展示成本类科目，这里还需要再收窄。
  - 这属于筛选口径问题，不是阻塞级回归。

## 最终裁决

- 结论：**通过**
- 阻塞项：**无**
- 非阻塞项：上述两条

