# ISSUE-005-004 库存与领料列表页生产化补强

日期：2026-07-09

## 结论

- 实现状态：已按 Ready Issue 范围完成最小前端补强。
- 验收状态：通过。
- 阻塞类型：无。

## 本轮正式交付

- 本报告
- `docs/iterations/iteration-2026-07-08-report.md` 中的通过记录
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md` 状态更新
- 库存台账页与领料申请页前端补强代码及最小测试

## 补强点

- 库存台账页：
  - 查询条件、分页参数写入路由 query，刷新后可回显。
  - 新增显式错误态、空态和重试入口。
  - 保持既有库存数量展示、交易类型/来源类型标签和流水详情入口，不改库存数量业务口径。
- 领料申请页：
  - 查询条件、分页参数写入路由 query，刷新后可回显。
  - 新增显式错误态、空态和重试入口。
  - 保持既有出库状态、审批状态、提交审批和删除入口，不改领料状态业务含义。
- 通用：
  - 复用既有 `listPageQuery` 小工具，不新增依赖。
  - 新增最小测试，覆盖库存页与领料页的 query 持久化和反馈态接线。

## 验收矩阵

| 项目 | 结果 | 依据 |
| --- | --- | --- |
| 库存筛选回显 | 通过 | Vitest 守卫确认 `projectId`、`warehouseId`、`materialId`、`keyword`、`pageNo`、`pageSize` 从路由恢复并回写 |
| 库存分页保留 | 通过 | Vitest 守卫确认 `pageNo/pageSize` 使用 `listPageQuery` 读写并执行 `router.replace` |
| 库存 loading/empty/error/retry | 通过 | 源码守卫确认 `hasLoaded`、`listError`、`a-result`、`LgEmptyState`、`fetchData` 重试入口存在 |
| 领料筛选回显 | 通过 | Vitest 守卫确认 `projectId`、`warehouseId`、`approvalStatus`、`requisitionCode`、`pageNo`、`pageSize` 从路由恢复并回写 |
| 领料分页保留 | 通过 | Vitest 守卫确认 `pageNo/pageSize` 使用 `listPageQuery` 读写并执行 `router.replace` |
| 领料 loading/empty/error/retry | 通过 | 源码守卫确认 `hasLoaded`、`listError`、`a-result`、`LgEmptyState`、`fetchData` 重试入口存在 |
| 业务口径不回退 | 通过 | 库存页保留既有 `fmtQty`、交易标签与详情逻辑；领料页保留既有 `stockOutFlag`、`approvalStatus === DRAFT` 判断 |

## 验证命令

- `cd frontend-admin; pnpm exec vitest run src/composables/__tests__/listPageQuery.test.ts src/pages/inventory/__tests__/stock-production.test.ts src/pages/requisition/__tests__/list-production.test.ts`：通过，`3` 个文件、`7` 个用例全部通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

## 剩余风险

- 本轮未做真实浏览器验收，结论基于指定 Vitest、类型检查、构建和代码审查。
- 本轮未跑全量前端测试，结论限于 ISSUE-005-004 指定库存/领料列表生产化范围。
