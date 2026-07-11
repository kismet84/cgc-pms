# ISSUE-008-008：WBS、进度计划与甘特图 最小可行回归

完成日期：2026-07-09

## 目标

- 基于现有架构补齐“WBS、进度计划与甘特图”的一轮最小可验收能力或回归断言。
- 不扩大为完整平台化改造，不连接生产环境。
- 不新增 migration，不引入新甘特图库，不新增 `schedule_*` 表。

## 修改范围

- `backend/src/test/java/com/cgcpms/subcontract/SubTaskControllerTest.java`
  - 复用现有 `sub_task` 分包任务接口作为最小进度计划载体。
  - 新增 WBS 编码自动生成、工作区域、计划开始 / 完成、实际开始、进度百分比、状态字段断言。
  - 新增按 `projectId + taskName` 查询进度行断言，证明当前接口可为只读甘特图提供项目内任务行，且仍走既有鉴权和租户过滤。

## 验收证据

- `cd backend; .\mvnw.cmd "-Dtest=SubTaskControllerTest" test`
  - 通过，`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。
- `cd backend; .\mvnw.cmd test`
  - 未通过；Surefire 汇总 `1565` 个测试、`11` 个 failures、`29` 个 errors、`1` 个 skipped。
  - 失败类集中在既有 `DashboardChiefEngineerServiceTest`、`InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`、`Phase2FullChainIntegrationTest`、`Phase4IntegrationTest`、`PurchaseRequestServiceTest`、`ContractRevenueServiceTest` 和旧 `workflow` 测试类；新增 `SubTaskControllerTest` 不在失败列表。
- `cd frontend-admin; pnpm type-check`
  - 通过，`vue-tsc --noEmit` exit 0。
- `git diff --check`
  - 通过，仅有既有工作区文件换行符转换提示。

## 结论

结论：通过（本 Issue 目标通过；后端全量仍有既有无关红灯）。

阻塞：无。

剩余风险：
- 本轮只留下最小可验收接口回归，不新增独立 `schedule` 模块、任务依赖表、基线、计划变更审计或拖拽排程。
- 甘特图能力当前只验证后端可返回项目内任务行所需的 WBS 编码、日期和进度字段，不新增前端甘特组件。
- 后端全量测试仍有既有无关失败，需由对应 Ready Issue 分别治理。
