# ISSUE-005-005 分包与结算列表页生产化补强

日期：2026-07-09

## 结论

- 实现状态：已按 Ready Issue 范围完成最小前端补强。
- 验收状态：通过。
- 阻塞类型：无。

## 本轮正式交付

- 本报告
- `docs/iterations/iteration-2026-07-08-report.md` 中的通过记录
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md` 状态更新
- 分包任务页与结算列表页前端补强代码及最小测试

## 补强点

- 分包任务页：
  - 查询条件、分页参数写入路由 query，刷新后可回显。
  - 覆盖 `projectId`、`status`、`keyword`、`pageNo`、`pageSize`。
  - 新增显式错误态、空态和重试入口。
  - 保持既有任务状态标签、进度百分比、编辑/删除入口，不改分包任务状态业务含义。
- 结算列表页：
  - 查询条件、分页参数写入路由 query，刷新后可回显。
  - 覆盖 `projectId`、`settlementStatus`、`keyword`、`pageNo`、`pageSize`。
  - 新增显式错误态、空态和重试入口。
  - 保持既有结算状态标签、金额展示、`FINALIZED` 删除保护和 KPI 摘要，不改状态字段、金额字段或付款关联业务含义。
- 通用：
  - 复用既有 `listPageQuery` 小工具和 `LgEmptyState`，不新增依赖。
  - 新增最小测试，覆盖两页 query 持久化和反馈态接线。

## 验收矩阵

| 项目 | 结果 | 依据 |
| --- | --- | --- |
| 分包筛选回显 | 通过 | Vitest 守卫确认 `projectId`、`status`、`keyword`、`pageNo`、`pageSize` 从路由恢复并回写 |
| 分包分页保留 | 通过 | Vitest 守卫确认 `pageNo/pageSize` 使用 `listPageQuery` 读写并执行 `router.replace` |
| 分包 loading/empty/error/retry | 通过 | 源码守卫确认 `hasLoaded`、`listError`、`a-result`、`LgEmptyState`、`fetchData` 重试入口存在 |
| 结算筛选回显 | 通过 | Vitest 守卫确认 `projectId`、`settlementStatus`、`keyword`、`pageNo`、`pageSize` 从路由恢复并回写 |
| 结算分页保留 | 通过 | Vitest 守卫确认 `pageNo/pageSize` 使用 `listPageQuery` 读写并执行 `router.replace` |
| 结算 loading/empty/error/retry | 通过 | 源码守卫确认 `hasLoaded`、`listError`、`a-result`、`LgEmptyState`、`fetchData` 重试入口存在 |
| 业务口径不回退 | 通过 | 分包页保留既有 `STATUS_LABEL/STATUS_COLOR`、进度条与编辑删除逻辑；结算页保留既有 `SETTLEMENT_STATUS_LABEL`、`rowSettlementAmount`、`settlementStatusOf(row) !== 'FINALIZED'` 判断 |

## 验证命令

- `cd frontend-admin; pnpm exec vitest run src/composables/__tests__/listPageQuery.test.ts src/pages/subcontract/__tests__/task.test.ts src/pages/settlement/__tests__/index.test.ts`：通过，`3` 个文件、`10` 个用例全部通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

## 剩余风险

- 本轮未做真实浏览器验收，结论基于指定 Vitest、类型检查、构建和代码审查。
- 本轮未跑全量前端测试，结论限于 ISSUE-005-005 指定分包/结算列表生产化范围。
