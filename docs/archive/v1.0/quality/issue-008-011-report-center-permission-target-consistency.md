# ISSUE-008-011 报表中心平台化缺口-M2：目录权限过滤与入口一致性回归

日期：2026-07-10
Issue：ISSUE-008-011 报表中心平台化缺口-M2：目录权限过滤与入口一致性回归
类型：正式归档 / backlog 收口 / 质量报告 / 本地提交收口
结论：通过 / 非阻塞

## 1. 收口范围

本轮仅收口 `ISSUE-008-011` 白名单改动：

- `backend/src/test/java/com/cgcpms/report/ReportCatalogServiceTest.java`
- `frontend-admin/src/pages/report/catalog.vue`
- `frontend-admin/src/pages/report/catalog-entry.ts`
- `frontend-admin/src/pages/report/__tests__/catalog-entry.test.ts`
- `docs/backlog/**`
- `docs/quality/**`
- `docs/iterations/**`

不纳入本轮：

- `AGENTS.override.md`
- `plugins/cgc-pms-autopilot/**`
- `docs/backlog/current-focus.md`
- 其他非本 Issue 脏改动

## 2. 实现与回归事实

本轮最小实现闭环如下：

1. 后端测试补强了非管理员目录过滤断言：仅保留当前权限命中的目录项，且 `permissionCode` 为空的 `workflow-efficiency` 继续可见。
2. 前端把“是否可跳转报表页面”的判定收敛到 `canOpenReportCatalogPage`，避免 `API-only` 目录项被当作页面入口伪跳转。
3. 前端新增独立测试，覆盖“真实页面可跳转 / API-only 不可跳转 / 未注册页面不可跳转”三类入口一致性断言。

## 3. 验证证据

本轮实际执行并通过：

1. `cd backend; .\mvnw.cmd "-Dtest=ReportCatalogServiceTest" test`
   - 结果：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
2. `cd frontend-admin; pnpm exec vitest run src/pages/report/__tests__/catalog-entry.test.ts`
   - 结果：`Test Files 1 passed`，`Tests 3 passed`
3. `cd frontend-admin; pnpm type-check`
   - 结果：通过
4. `cd frontend-admin; pnpm build`
   - 结果：通过
5. `git diff --check`
   - 结果：通过

验证结论：

- 目录权限过滤断言已落到 `ReportCatalogServiceTest`，能证明无权限目录不会泄漏。
- 页面型与 `API-only` 入口的前端判定已具备独立回归守卫，避免伪跳转到 `404`。
- 本轮未新增 migration，未放宽租户、项目、角色或权限边界。

## 4. Backlog 同步动作

本轮最小同步如下：

- 从 `docs/backlog/ready-issues.md` 移除 `ISSUE-008-011` 的 Ready 状态，并将 Ready 队列更新为当前为空。
- 在 `docs/backlog/done-issues.md` 新增 `ISSUE-008-011` 完成记录。
- 在 `docs/iterations/iteration-2026-07-10-report.md` 记录本次正式归档与本地提交收口。

## 5. Git 收口边界

本轮 Git 收口遵守以下边界：

1. 提交前仅允许 stage 本 Issue 白名单文件。
2. `AGENTS.override.md`、`plugins/cgc-pms-autopilot/**` 与其他非本 Issue 脏改动全部保留不动。
3. 不执行 push。

## 6. 最终裁决

正式交付物：

- `backend/src/test/java/com/cgcpms/report/ReportCatalogServiceTest.java`
- `frontend-admin/src/pages/report/catalog.vue`
- `frontend-admin/src/pages/report/catalog-entry.ts`
- `frontend-admin/src/pages/report/__tests__/catalog-entry.test.ts`
- `docs/quality/issue-008-011-report-center-permission-target-consistency.md`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-10-report.md`

验收证据：

- `ReportCatalogServiceTest`：`2` 个用例通过，`0` failures，`0` errors。
- `catalog-entry.test.ts`：`3` 个用例通过。
- `pnpm type-check`：通过。
- `pnpm build`：通过。
- `git diff --check`：通过。

临时产物：

- `backend/target/**`、`frontend-admin/dist/**` 属于本地验证产物，未纳入提交。

结论：通过。
阻塞：无。
剩余风险：

1. 当前 Ready 队列已空，下一条报表中心相关 Ready 仍需主线程重新拆题，不能把本轮最小闭环外推为“报表中心平台化已整体完成”。
2. 工作区仍存在非本 Issue 未提交改动，但本轮已与提交范围隔离。
