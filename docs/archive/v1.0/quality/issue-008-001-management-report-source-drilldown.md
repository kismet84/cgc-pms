# ISSUE-008-001 经营总览报表口径与来源下钻回归

完成日期：2026-07-09

## 目标

- 建立项目经营总览报表的最小可用口径，确保汇总指标能追溯到现有来源单据或下钻数据。
- 不扩大为完整报表中心、异步导出平台或报表定义模型。

## 修改范围

- `backend/src/main/java/com/cgcpms/dashboard/vo/ManagementDashboardVO.java`
- `backend/src/main/java/com/cgcpms/dashboard/service/DashboardFinanceManagementService.java`
- `backend/src/test/java/com/cgcpms/dashboard/service/DashboardFinanceManagementServiceTest.java`
- `frontend-admin/src/types/dashboard.ts`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-09-report.md`

## 回归口径

- 管理驾驶舱 `ManagementDashboardVO` 增加 `metricSources`，以当前 `projectRankings` 的项目汇总结果作为经营总览指标来源。
- 每条来源记录包含 `sourceType=PROJECT_SUMMARY`、`sourceId=projectId`、项目名称以及合同收入、动态成本、预计利润、已付金额。
- `metricSources` 只由已存在的项目汇总排名派生；没有可用项目汇总时不额外伪造明细。
- 未新增数据库 migration、报表定义表、异步导出任务或外部报表平台。

## 权限、租户与数据边界

- 本轮未修改 controller 鉴权注解、权限码、租户过滤或项目访问校验。
- 指标来源来自管理驾驶舱已按当前租户查询出的 active project 汇总，不新增跨租户读取路径。
- 未修改 `backend/src/main/resources/db/migration/**`、`deploy/**`、生产凭据或外部平台配置。

## 验证证据

- `http://localhost:8080/api/actuator/health`：通过，HTTP 200。
- `http://localhost:5173/`：通过，HTTP 200。
- `http://localhost:5173/api/auth/dev-login?redirect=/dashboard`：通过，HTTP 302，Location `/dashboard`。
- `cd backend; .\mvnw.cmd "-Dtest=DashboardFinanceManagementServiceTest#testManagementView" test`：首次因测试代码未保存 `SeedResult` 返回值编译失败；修正后通过，`1` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd backend; .\mvnw.cmd test`：未通过；失败类集中在既有 `DashboardChiefEngineerServiceTest`、`InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`、`Phase2FullChainIntegrationTest`、`Phase4IntegrationTest`、`PurchaseRequestServiceTest`、`ContractRevenueServiceTest` 和多组旧 `workflow` 测试夹具/断言问题；本轮目标类已通过，未见本轮经营总览来源下钻改动引入的失败。
- `git diff --check`：通过。

## 全量失败分类

- `DashboardChiefEngineerServiceTest`：既有 dashboard 演示项目/技术事项测试数据前置问题。
- `InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`：既有发票校验与项目关系测试前置或断言问题。
- `Phase2FullChainIntegrationTest`、`Phase4IntegrationTest`：既有跨阶段集成夹具、金额边界或审批业务对象前置问题。
- `PurchaseRequestServiceTest`、`ContractRevenueServiceTest`：既有采购/收入提交审批前置或断言问题。
- `WorkflowApproverResolverTest`、`WorkflowConcurrencyTest`、`WorkflowCoreServiceTest`、`WorkflowEngineIntegrationTest`、`WorkflowTemplateManagementTest`：既有 workflow 测试业务类型或夹具问题。

## 自审结论

PASS。

依据：
- 经营总览管理视图汇总指标已有稳定回归断言，并补充了可追溯到项目汇总来源的 `metricSources`。
- 缺失来源时不会伪造明细，来源列表与实际项目排名同源、同数量。
- 本轮未放宽 dashboard 鉴权、租户与项目边界。

## 结论

通过 / 非阻塞。

剩余风险：
- 本轮只做管理驾驶舱经营总览的最小来源下钻，不新增完整报表中心、导出中心或报表定义模型。
- 后端全量测试仍存在既有无关红灯，需要后续 Ready Issue 分别治理。
