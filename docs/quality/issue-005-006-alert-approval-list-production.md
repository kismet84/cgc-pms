# ISSUE-005-006 预警与审批列表页生产化补强

日期：2026-07-09

## 结论

- 实现状态：已按 Ready Issue 范围完成最小前端补强。
- 验收状态：通过。
- 阻塞类型：无。

## 本轮正式交付

- 本报告
- `docs/iterations/iteration-2026-07-08-report.md` 中的通过记录
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md` 状态更新
- 预警列表与审批列表前端补强代码及最小测试

## 补强点

- 预警列表页：
  - 查询条件、分页参数写入路由 query，刷新后可回显。
  - 覆盖 `keyword`、`projectId`、`alertDomain`、`ruleType`、`severity`、`isRead`、`processStatus`、`triggeredStart`、`triggeredEnd`、`onlyDefaultScope`、`pageNo`、`pageSize`。
  - 新增显式错误态、空态和重试入口。
  - 保持既有默认权限域预设、默认范围过滤、批量处理、业务单据跳转与订阅能力，不改预警规则和权限边界。
- 审批列表页：
  - 查询条件、分页参数写入路由 query，刷新后可回显。
  - 覆盖 `keyword`、`businessType`、`instanceStatus`、`startTime`、`endTime`、`pageNo`、`pageSize`。
  - 新增显式错误态、空态和重试入口。
  - 切换待办/已办/抄送/我发起时保留筛选条件，不改审批待办/已办/我发起口径、详情入口和动作边界。
- 通用：
  - 复用既有 `listPageQuery` 小工具和 `LgEmptyState`，不新增依赖。
  - 新增最小测试，覆盖两页 query 持久化和反馈态接线。

## 验收矩阵

| 项目 | 结果 | 依据 |
| --- | --- | --- |
| 预警筛选回显 | 通过 | Vitest 守卫确认 query helper 接入 `keyword/projectId/severity/isRead/processStatus/pageNo/pageSize`，源码确认 `alertDomain/ruleType/triggeredStart/triggeredEnd/onlyDefaultScope` 同步 |
| 预警分页保留 | 通过 | 源码与 Vitest 守卫确认 `pageNo/pageSize` 使用 `listPageQuery` 读写并执行 `router.replace` |
| 预警 loading/empty/error/retry | 通过 | 源码守卫确认 `hasLoaded`、`listError`、`a-result`、`LgEmptyState`、`handleRetry` 存在 |
| 审批筛选回显 | 通过 | Vitest 守卫确认 `keyword/businessType/instanceStatus/startTime/endTime/pageNo/pageSize` 从路由恢复并回写 |
| 审批分页保留 | 通过 | 源码与 Vitest 守卫确认 `pageNo/pageSize` 使用 `listPageQuery` 读写并执行 `router.replace` |
| 审批 loading/empty/error/retry | 通过 | 源码守卫确认 `hasLoaded`、`listError`、`a-result`、`LgEmptyState`、`fetchData` 重试入口存在 |
| 业务口径不回退 | 通过 | 预警页保留 `resolveRoleDefaultPreset/resolveSearchAlertDomain`；审批页保留 `getMyTodos/getMyDone/getMyCc/getMyInitiatedInstances`、详情入口和动作口径 |

## 验证命令

- `cd frontend-admin; pnpm exec vitest run src/composables/__tests__/listPageQuery.test.ts src/pages/approval/__tests__/ApprovalWorkList.test.ts src/pages/alert/__tests__/index.test.ts`：通过，`3` 个文件、`25` 个用例全部通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

## 剩余风险

- 本轮未做真实浏览器验收，结论基于指定 Vitest、类型检查、构建和代码审查。
- 本轮未跑全量前端测试，结论限于 ISSUE-005-006 指定预警/审批列表生产化范围。
