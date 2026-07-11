# `fix/audit-issues-20260705` 分支审计报告

## 结论

不通过，当前**不可收口**。

理由很直接：这个分支引入了至少两条会影响现网行为的回归，且都没有看到对应的补偿逻辑或配套测试更新。

## 关键问题

### 1. 用户态缓存去掉 `roles/permissions`，会击穿刷新后的权限恢复

- 严重级别：高
- 位置：
  - [`frontend-admin/src/stores/user.ts`](../../../../frontend-admin/src/stores/user.ts):11-15
  - [`frontend-admin/src/stores/user.ts`](../../../../frontend-admin/src/stores/user.ts):66-77
  - [`frontend-admin/src/router/index.ts`](../../../../frontend-admin/src/router/index.ts):487-517
  - [`frontend-admin/src/directives/permission.ts`](../../../../frontend-admin/src/directives/permission.ts):10-11
- 证据：
  - `userInfo` 仍然被 `isLogin = !!userInfo.value` 视为“已登录”。
  - `persistUserInfo()` 现在只落盘 `userId / username / roleName`，不再持久化 `roles / permissions`。
  - `restoreUserSession()` 在 `userInfo` 存在时直接返回，不会再请求 `/auth/userinfo` 补回权限。
  - 路由守卫和 `v-permission` 都依赖 `roles/permissions`。
- 影响：
  - 用户刷新页面后会保留“已登录”的假象，但 `roles/permissions` 为空。
  - 管理员入口、权限指令、需要权限码的页面会误判为无权限。
  - 这不是 UI 文案问题，是实际登录态/权限态回归。
- 最小修复建议：
  - 要么继续持久化 `roles/permissions`。
  - 要么在 `restoreUserSession()` 里把“只有基础用户信息”视为不完整登录态，主动刷新 `/auth/userinfo`。

### 2. 成本列表页删掉了现有筛选能力，但后端和现有测试仍在依赖这些参数

- 严重级别：高
- 位置：
  - [`frontend-admin/src/pages/cost/ledger.vue`](../../../../frontend-admin/src/pages/cost/ledger.vue):44-48, 126-129, 152-155, 174-197
  - [`backend/src/main/java/com/cgcpms/cost/service/CostLedgerService.java`](../../../../backend/src/main/java/com/cgcpms/cost/service/CostLedgerService.java):46-47, 70-71, 151-161
  - [`frontend-admin/src/pages/cost/__tests__/CostLedgerProduction.test.ts`](../../../../frontend-admin/src/pages/cost/__tests__/CostLedgerProduction.test.ts):19-28
- 证据：
  - 当前前端筛选状态只保留了 `projectId / contractId / partnerId / costStatus`，`costSubjectId / costType / sourceType` 已被移除。
  - 后端 `CostLedgerService` 仍然接收并使用 `costSubjectId / costType / sourceType` 做查询过滤。
  - 现有生产测试仍明确断言页面里存在 `filter.costType` 和对应查询参数。
- 影响：
  - 这是功能回退，不是纯展示调整。
  - 用户会失去原本可用的成本科目、成本类型、来源类型筛选。
  - 现有测试和前后端契约都已经和当前页面实现不一致。
- 最小修复建议：
  - 如果这是故意收缩功能，必须同步更新后端契约说明和相关测试。
  - 如果不是故意收缩，恢复这些筛选项和路由查询回填。

### 3. 合同乐观锁只加了字段和 migration，没有真正接到更新链路

- 严重级别：中
- 位置：
  - [`backend/src/main/java/com/cgcpms/contract/entity/CtContract.java`](../../../../backend/src/main/java/com/cgcpms/contract/entity/CtContract.java):92-97
  - [`backend/src/main/java/com/cgcpms/contract/service/CtContractService.java`](../../../../backend/src/main/java/com/cgcpms/contract/service/CtContractService.java):205-215
  - [`backend/src/main/java/com/cgcpms/contract/service/CtContractService.java`](../../../../backend/src/main/java/com/cgcpms/contract/service/CtContractService.java):250-251
  - [`backend/src/main/java/com/cgcpms/contract/service/CtContractService.java`](../../../../backend/src/main/java/com/cgcpms/contract/service/CtContractService.java):326
  - [`frontend-admin/src/types/contract.ts`](../../../../frontend-admin/src/types/contract.ts):42-71
  - [`frontend-admin/src/api/modules/contract.ts`](../../../../frontend-admin/src/api/modules/contract.ts):39-40, 128-137
- 证据：
  - 实体新增了 `@Version private Integer version`，并且迁移脚本也新增了 `version` 列。
  - 但常规 `update()` 仍然使用 `ctContractMapper.update(null, new LambdaUpdateWrapper<>())`，没有带版本条件。
  - 前端 `ContractVO` 没有 `version` 字段，`create/update/save/updateDraft` 请求也没有携带版本。
  - `compositeSave()` 虽然走 `updateById(contract)`，但前端和接口层没传 version，实际也不会形成并发保护闭环。
- 影响：
  - 这条改动会给人一种“已经上了乐观锁”的假象，但实际写路径并没有并发保护。
  - 如果多人同时编辑合同，最后写仍可能覆盖先写。
- 最小修复建议：
  - 要么把 version 完整贯通到 VO / API / 表单提交 / 更新链路。
  - 要么删掉这个半成品版本字段，避免制造错误安全感。

## 可收口判断

- 结论：**不可收口**
- 阻塞项：
  - 用户态缓存导致的权限恢复回归
  - 成本列表筛选能力回退
- 非阻塞但需要补齐：
  - 合同乐观锁链路未闭环

## 备注

- 本次审计基于当前工作树的未提交改动，不是 `master...HEAD` 的提交差异。
- 当前工作树还包含若干临时产物和目录改动，建议在正式收口前再做一次清理和 `git status` 复核。
