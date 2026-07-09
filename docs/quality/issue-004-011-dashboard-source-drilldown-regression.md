# ISSUE-004-011 驾驶舱汇总指标来源单据下钻回归

日期：2026-07-09

## 目标

- 回归驾驶舱汇总指标与来源单据下钻链路，确保指标可解释、来源可定位。
- 不扩大为驾驶舱重设计或新增报表中心能力。

## 修改范围

- `backend/src/main/java/com/cgcpms/dashboard/vo/CostManagerDashboardVO.java`
- `backend/src/main/java/com/cgcpms/dashboard/service/DashboardCostService.java`
- `backend/src/test/java/com/cgcpms/dashboard/service/DashboardCostServiceTest.java`
- `frontend-admin/src/types/dashboard.ts`
- `frontend-admin/src/pages/dashboard/components/DashboardCostView.vue`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-09-report.md`

## 回归口径

- 成本经理驾驶舱 `ledgerRows` 增加最小来源标识：`sourceType`、`sourceId`。
- 成本科目汇总行在能定位单一合同时返回 `sourceType=CONTRACT` 与合同 `sourceId`；多合同汇总或缺失合同时保留为 `COST_SUBJECT`。
- 合同台账行返回 `sourceType=CONTRACT`；资金支付行返回 `sourceType=PAY_RECORD`。
- 前端下钻复用已有页面：合同来源直接进入合同详情；付款与成本来源继续进入既有付款申请/成本台账页，并透传 `sourceType/sourceId/projectId/costSubjectId` 查询参数。

## 权限、租户与数据边界

- 本轮未修改 controller 鉴权注解、权限码、租户过滤或项目访问校验。
- 后端来源标识来自当前租户、当前项目已查询到的合同、付款记录和成本科目结果，不新增跨租户读取路径。
- 未修改 `backend/src/main/resources/db/migration/**`、`deploy/**`、生产凭据或外部平台配置。

## 验证证据

- `http://localhost:8080/api/actuator/health`：通过，HTTP 200。
- `http://localhost:5173/`：通过，HTTP 200。
- `http://localhost:5173/api/auth/dev-login?redirect=/dashboard`：通过，跟随重定向后 HTTP 200。
- `cd backend; .\mvnw.cmd "-Dtest=DashboardCostServiceTest" test`：通过，`11` 个用例通过；新增断言覆盖成本、合同、资金三类台账行来源标识。
- `cd backend; .\mvnw.cmd test`：未通过，失败类集中在既有 `DashboardChiefEngineerServiceTest`、`InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`、`Phase2FullChainIntegrationTest`、`Phase4IntegrationTest`、`PurchaseRequestServiceTest`、`ContractRevenueServiceTest` 和旧 `workflow` 测试夹具/断言问题；本轮目标类 `DashboardCostServiceTest` 已通过，未见本轮来源下钻改动引入的失败。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd frontend-admin; pnpm build`：通过。
- `git diff --check`：通过。

## 全量失败分类

- `DashboardChiefEngineerServiceTest`：既有 dashboard 演示项目/技术事项测试数据前置问题。
- `InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`：既有发票校验与软删除测试前置或断言问题。
- `Phase2FullChainIntegrationTest`、`Phase4IntegrationTest`：既有跨阶段集成夹具与金额边界问题。
- `PurchaseRequestServiceTest`、`ContractRevenueServiceTest`：既有采购/收入提交审批前置或断言问题。
- `WorkflowApproverResolverTest`、`WorkflowConcurrencyTest`、`WorkflowCoreServiceTest`、`WorkflowEngineIntegrationTest`、`WorkflowTemplateManagementTest`：既有 workflow 测试业务类型或夹具问题。

## 结论

通过。驾驶舱成本汇总、合同台账和资金支付三类来源已具备可断言的来源标识，前端下钻复用既有页面并透传来源参数；本轮未改变权限、租户、schema 或生产配置。全量后端测试仍存在既有无关红灯，需后续 Ready Issue 分别治理。
