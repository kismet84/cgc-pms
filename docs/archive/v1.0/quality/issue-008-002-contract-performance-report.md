# ISSUE-008-002 合同履约报表口径回归

完成日期：2026-07-09

## 目标

- 回归合同履约报表的合同金额、变更金额、付款进度和履约状态口径。
- 不改合同业务语义，不新增合同履约专用表。

## 修改范围

- `backend/src/main/java/com/cgcpms/contract/vo/ContractPerformanceReportVO.java`
- `backend/src/main/java/com/cgcpms/contract/service/CtContractService.java`
- `backend/src/test/java/com/cgcpms/contract/CtContractServiceTest.java`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-09-report.md`

## 回归口径

- 新增 `CtContractService#getPerformanceReport(projectId)`，复用现有合同、合同变更、付款记录数据。
- 合同金额取 `ct_contract.contractAmount`。
- 变更金额只累计 `ct_contract_change.approvalStatus = APPROVED` 的 `changeAmount`，不统计草稿变更。
- 付款金额只累计 `pay_record.payStatus = SUCCESS` 的 `payAmount`，不统计待审批付款。
- 单合同付款进度按 `SUCCESS付款金额 / currentAmount` 计算；合计进度按 `总SUCCESS付款金额 / (总合同金额 + 总已审批变更金额)` 计算。

## 权限、租户与数据边界

- 查询始终带当前 `tenantId`；按项目查询时复用 `ProjectAccessChecker.checkAccess`。
- 本轮未修改合同状态机、审批状态机、controller 鉴权、schema、生产配置或外部平台连接。
- 未修改 `backend/src/main/resources/db/migration/**`、`deploy/**`、生产凭据。

## 验证证据

- `http://localhost:8080/api/actuator/health`：通过，HTTP 200。
- `http://localhost:5173/`：通过，HTTP 200。
- `http://localhost:5173/api/auth/dev-login?redirect=/dashboard`：通过，HTTP 302，Location `/dashboard`。
- `cd backend; .\mvnw.cmd "-Dtest=CtContractServiceTest#testPerformanceReportAggregatesContractChangeAndPayment" test`：通过，`1` 个用例通过。
- `cd backend; .\mvnw.cmd test`：未通过；失败类仍集中在既有 `DashboardChiefEngineerServiceTest`、`InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`、`Phase2FullChainIntegrationTest`、`Phase4IntegrationTest`、`PurchaseRequestServiceTest`、`ContractRevenueServiceTest` 和多组旧 `workflow` 测试夹具/断言问题；本轮目标类已通过，未见本轮合同履约报表改动引入的失败。
- `git diff --check`：通过。

## 自审结论

PASS。

依据：
- 稳定断言覆盖合同金额、已审批变更金额、SUCCESS 付款金额、付款进度和合同履约状态。
- 草稿变更与待审批付款不会进入履约报表统计。
- 本轮未放宽合同查询的租户、项目和角色边界。

## 结论

通过 / 非阻塞。

剩余风险：
- 本轮只新增后端最小报表聚合方法，未新增前端页面、导出能力或通用报表中心。
- 后端全量测试仍存在既有无关红灯，需要后续 Ready Issue 分别治理。
