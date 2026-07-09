# ISSUE-008-005 审批效率报表口径回归

完成日期：2026-07-09

## 目标

- 回归审批效率报表的待办数量、已办数量、超时/耗时和审批状态口径。
- 不改审批状态机，不新增审批分析专用表。

## 修改范围

- `backend/src/main/java/com/cgcpms/workflow/vo/WfEfficiencyVO.java`
- `backend/src/main/java/com/cgcpms/workflow/controller/WorkflowController.java`
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowQueryService.java`
- `backend/src/test/java/com/cgcpms/workflow/WorkflowQueryServiceTest.java`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-09-report.md`

## 回归口径

- 新增 `GET /workflow/statistics/efficiency` 只读接口。
- 待办数量复用 `getMyTodos` 的当前租户、当前处理人、`PENDING` 任务和 `RUNNING` 实例口径。
- 已办数量复用 `getMyDone` 的当前租户、当前操作人和 `APPROVE/REJECT/TRANSFER/ADD_SIGN` 记录口径。
- 超时待办按仍处于待办列表口径内，且 `receivedAt` 早于 `now - overdueHours` 统计；默认 `overdueHours=48`。
- 平均耗时按当前用户已处理任务的 `handledAt - receivedAt` 分钟数统计。
- 审批状态分布按“我发起”实例口径统计，不扩大跨租户、跨发起人范围。

## 权限、租户与数据边界

- 接口要求 `isAuthenticated()`，用户与租户从 `UserContext` 获取。
- 本轮未修改审批状态机、审批处理动作、实例推进逻辑或通知/预警联动。
- 本轮未新增 migration、审批分析专用表、通用报表中心表、生产配置或外部平台连接。
- 未修改 `backend/src/main/resources/db/migration/**`、`deploy/**`、生产凭据。

## 验证证据

- `http://localhost:8080/api/actuator/health`：通过，HTTP 200。
- `http://localhost:5173/`：通过，HTTP 200。
- `http://localhost:5173/api/auth/dev-login?redirect=/dashboard`：通过，HTTP 200。
- `cd backend; .\mvnw.cmd "-Dtest=WorkflowQueryServiceTest#getMyEfficiencyUsesWorkflowListSemantics" test`：通过，`1` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=WorkflowQueryServiceTest" test`：通过，`32` 个用例通过。
- `cd backend; .\mvnw.cmd test`：未通过；失败类仍集中在既有 `DashboardChiefEngineerServiceTest`、`InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`、`Phase2FullChainIntegrationTest`、`Phase4IntegrationTest`、`PurchaseRequestServiceTest`、`ContractRevenueServiceTest` 和旧 `workflow` 测试夹具/断言问题；本轮目标类已通过。
- `git diff --check`：通过。

## 自审结论

PASS。

依据：
- 稳定断言覆盖待办数量、已办数量、超时待办、平均耗时和审批状态分布。
- 统计口径复用审批中心已有列表边界，未放宽租户、发起人、处理人或实例状态范围。
- 本轮只新增 workflow 域最小只读报表能力，不新增表、不改状态机。

## 结论

通过 / 非阻塞。

剩余风险：
- “超时”当前基于待办接收时间和请求阈值推导；仓库现有 `wf_task` 无独立截止时间字段，本轮不新增 schema。
- 后端全量测试仍存在既有无关红灯，需要后续 Ready Issue 分别治理。
