# ISSUE-005-003 采购与收货列表页生产化补强

日期：2026-07-09

## 结论

- 实现状态：已按 Ready Issue 范围完成最小前端补强。
- 验收状态：通过。
- 阻塞类型：无。

## 本轮正式交付

- 本报告
- `docs/iterations/iteration-2026-07-08-report.md` 中的通过记录
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md` 状态更新
- 采购订单页与材料验收页前端补强代码及最小测试

## 补强点

- 采购订单列表：
  - 查询条件、分页参数写入路由 query，刷新后可回显。
  - 新增显式错误态、空态和重试入口。
  - 保持既有 `businessId` 深链打开逻辑，不回退权限按钮与行操作。
- 材料验收列表：
  - 查询条件、分页参数写入路由 query，刷新后可回显。
  - 新增显式错误态、空态和重试入口。
  - 保持既有提交审批、删除、编辑入口和质量状态展示。
- 通用：
  - 复用既有 `listPageQuery` 小工具，不新增依赖。
  - 新增最小测试，覆盖采购页与收货页的 query 持久化和反馈态接线。

## 验收矩阵

| 项目 | 结果 | 依据 |
| --- | --- | --- |
| 采购筛选回显 | 通过 | Vitest 守卫确认 `projectId`、`contractId`、`partnerId`、`orderStatus`、`orderType`、`keyword`、`pageNo`、`pageSize` 从路由恢复并回写 |
| 采购分页保留 | 通过 | Vitest 守卫确认 `pageNo/pageSize` 使用 `listPageQuery` 读写并执行 `router.replace` |
| 采购 loading/empty/error/retry | 通过 | 源码守卫确认 `hasLoaded`、`listError`、`a-result`、`LgEmptyState`、`fetchData` 重试入口存在 |
| 收货筛选回显 | 通过 | Vitest 守卫确认 `projectId`、`orderId`、`receiptCode`、`qualityStatus`、`pageNo`、`pageSize` 从路由恢复并回写 |
| 收货分页保留 | 通过 | Vitest 守卫确认 `pageNo/pageSize` 使用 `listPageQuery` 读写并执行 `router.replace` |
| 收货 loading/empty/error/retry | 通过 | 源码守卫确认 `hasLoaded`、`listError`、`a-result`、`LgEmptyState`、`fetchData` 重试入口存在 |
| 权限按钮不回退 | 通过 | 采购页保留既有 `approvalStatus === APPROVAL_DRAFT` 判断；收货页保留既有 `approvalStatus === 'DRAFT'` 判断 |

## 验证命令

- `cd frontend-admin; pnpm exec vitest run src/composables/__tests__/listPageQuery.test.ts src/pages/purchase/__tests__/order.test.ts src/pages/purchase/__tests__/list-production.test.ts src/pages/receipt/__tests__/index.test.ts src/pages/receipt/__tests__/list-production.test.ts`：通过，`5` 个文件、`32` 个用例全部通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

## 剩余风险

- 本轮未做真实浏览器验收，结论基于指定 Vitest、类型检查、构建和代码审查。
- 本轮未跑全量前端测试，结论限于 ISSUE-005-003 指定采购/收货列表生产化范围。
