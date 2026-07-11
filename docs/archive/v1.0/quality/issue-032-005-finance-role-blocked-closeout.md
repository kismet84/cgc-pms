# ISSUE-032-005 财务真实角色阻塞收口报告

日期：2026-07-09
Issue：ISSUE-032-005 M3 财务真实角色缺失导致真实角色抽样无法完成
类型：blocked 收口 / 权限整改归档 / 队列状态同步
结论：通过（blocked 收口完成）/ 非阻塞

本报告只裁决 `ISSUE-032-005` 是否可以从 `blocked` 队列收口，不替代新的 live 浏览器/API 财务角色复验；本轮不修改业务代码、不新增 migration、不重跑测试。

## 1. 收口范围

允许修改范围：

- `docs/quality/**`
- `docs/iterations/**`
- `docs/backlog/**`

本轮归档依据仅包括三类正式事实：

1. 阻塞解除结果：`FINANCE` 非超管账号前置已解除。
2. 权限整改结果：后端通过 `V135` 修复财务只读权限，前端通过路由权限码修正对齐后端。
3. D 验收结果：后端测试通过、前端 `type-check` 通过、`git diff --check` 通过。

## 2. 原阻塞项与解除口径

原阻塞项来自 `docs/backlog/blocked-issues.md`：

- 缺少绑定 `FINANCE` 的非超管真实账号，导致第32主线 M3 财务真实角色抽样无法完成。

本轮收口口径：

- 上游已确认 `FINANCE` 非超管账号前置解除，因此 `ISSUE-032-005` 不再保留在 blocked 队列。
- 本轮不重复写入账号明细，也不在文档中扩散临时登录信息。

## 3. 权限整改事实

只读核对当前未提交实现差异，可确认本轮权限整改范围受控：

1. 后端新增：
   - `backend/src/main/resources/db/migration/V135__fix_finance_readonly_payment_settlement_permissions.sql`
   - `backend/src/main/resources/db/migration-h2/V135__fix_finance_readonly_payment_settlement_permissions.sql`
2. 前端路由权限码修正：
   - `frontend-admin/src/router/index.ts` 将 `Payment`、`PaymentApplication` 的权限码从 `payment:application:query` 对齐为 `payment:app:query`。
3. 权限矩阵断言补强：
   - `backend/src/test/java/com/cgcpms/workflow/WorkflowPermissionMatrixDemoSeedTest.java` 新增 `FINANCE` 角色只读断言。

断言口径显示：

- 保留只读查询权限：`payment:app:query`、`payment:record:query`、`settlement:query`。
- 明确不扩大写权限：`payment:record:writeback`、`payment:app:submit`、`settlement:submit` 均不应授予 `FINANCE` 只读角色。

裁决：

- 权限整改方向正确。
- 写权限未扩大。

## 4. D 验收引用

本轮不重跑测试，直接引用 D 的正式验收结果作为收口依据：

| 验收项 | 结果 | 本轮裁决 |
| --- | --- | --- |
| 后端测试 | 通过 | 采信 |
| 前端 `type-check` | 通过 | 采信 |
| `git diff --check` | 通过 | 采信 |

说明：

- 本轮只做文档收口，不新增新的测试结论。
- 若后续 D 或主线程对上述验收结论有更新，应以更新后的正式结论为准。

## 5. 未纳入本轮通过口径的事项

以下事项本轮未验证，因此不得写成已通过：

1. 未刷新本地 backend/frontend 运行态。
2. 未重做 `FINANCE` 真实账号的浏览器抽样。
3. 未重做 `/dashboard` 财务标签、`/api/dashboard/finance`、付款/发票/结算入口 live 复验。

因此，本报告的“通过”仅表示：

- `ISSUE-032-005` 作为 blocked 队列项已具备收口条件。
- 不表示新的 M3 live 角色抽样已经在本轮重新完成。

## 6. Backlog 同步动作

本轮最小同步如下：

- 从 `docs/backlog/blocked-issues.md` 移除 `ISSUE-032-005`。
- 在 `docs/backlog/done-issues.md` 新增 `ISSUE-032-005` 完成记录。
- 在 `docs/backlog/ready-issues.md` 记录当前队列状态，明确该问题不再占用 blocked/ready。
- 在 `docs/backlog/current-focus.md` 标注当前阶段说明，后续由 A 重新拆 Ready。

## 7. 最终裁决

正式交付物：

- `docs/quality/issue-032-005-finance-role-blocked-closeout.md`
- `docs/backlog/blocked-issues.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/backlog/current-focus.md`
- `docs/iterations/iteration-2026-07-09-report.md`

验收证据：

- 上游阻塞解除结果：`FINANCE` 非超管账号前置已解除。
- 权限整改证据：`V135`、前端路由权限码修正、`WorkflowPermissionMatrixDemoSeedTest` 只读断言。
- D 验收结果：后端测试通过、前端 `type-check` 通过、`git diff --check` 通过。

临时产物：无。

结论：通过。
阻塞：无。
剩余风险：

1. 本轮未刷新运行态，未重做财务角色 live 抽样。
2. 若后续 A 要继续拆财务域 Ready，应重新定义 live 验证目标，避免把本次 blocked 收口误写成新的端到端复验通过。
