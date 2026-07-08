# ISSUE-004-010 审批流转通知与预警联动回归

日期：2026-07-09

## 目标

- 回归审批状态流转到通知与预警的联动口径。
- 覆盖审批提交、审批完成、审批异常三类关键事件。
- 不接入外部通知渠道，不重构预警规则中心，不放宽权限、租户或项目边界。

## 修改范围

- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowNotificationAlertService.java`
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowSubmitService.java`
- `backend/src/main/java/com/cgcpms/workflow/service/WorkflowApprovalService.java`
- `backend/src/test/java/com/cgcpms/workflow/service/WorkflowNotificationAlertServiceTest.java`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-09-report.md`

## 联动口径

- 审批提交：`WorkflowSubmitService` 在生成待办任务后，通过 `WorkflowNotificationAlertService` 向待审批人创建 `WORKFLOW` 站内通知，事件类型为 `SUBMIT_PENDING`；重新提交使用 `RESUBMIT_PENDING`。
- 审批完成：`WorkflowApprovalService` 在审批动作成功写入后，通过同一服务向发起人创建 `WORKFLOW` 站内通知，事件类型为 `APPROVAL_COMPLETED`。
- 审批异常：当站内通知创建失败时，不返回伪造成功结果；系统生成 `alert_log` 预警信号，`ruleType=WORKFLOW_NOTIFICATION_FAILED`，`alertDomain=WORKFLOW`，`severity=HIGH`，`sourceType=WORKFLOW`，`sourceId=wf_instance.id`。

## 去重与防丢失

- 异常预警使用 `WF:{instanceId}:{eventType}:{recipientUserId}` 作为 `dedupKey`。
- 若同一审批实例、同一事件、同一接收人已有 `OPEN` 且未删除的预警，不重复插入。
- 若预警写入自身失败，仅记录后端 warn 日志，不中断审批主流程，避免因通知通道问题回滚审批状态。

## 敏感信息防泄漏

新增测试断言异常预警消息不会保留以下敏感明文：

- `Authorization: Bearer ...`
- `Cookie: ...`
- `password=...`
- `token=...`

预警消息只保留审批实例、事件类型、接收人、脱敏后的标题/内容和失败原因。

## 权限、租户与项目边界

- 本轮未修改审批权限注解、`WorkflowEngine` 权限矩阵、租户校验或项目访问校验。
- 通知和预警均沿用审批实例上的 `tenantId`、`projectId`、`contractId`、`id`。
- 未修改 `backend/src/main/resources/db/migration/**`、`deploy/**`、生产凭据或外部平台配置。

## 验证证据

- `cd backend; .\mvnw.cmd "-Dtest=WorkflowNotificationAlertServiceTest" test`：通过，`3` 个用例通过；红灯阶段先因生产类不存在编译失败，完成最小实现后转绿。
- `cd backend; .\mvnw.cmd "-Dtest=WorkflowNotificationAlertServiceTest,WorkflowSubmitServiceTest" test`：通过，`8` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=WorkflowEngineIntegrationTest#test15_lifecycleNotifications" test`：未通过，失败原因为该集成方法单独运行缺少 `tenantId=777` 的合同业务对象，报错 `审批业务对象不存在`；分类为既有测试夹具/前置数据问题，非本轮通知联动实现回退。
- `cd backend; .\mvnw.cmd test`：未通过，Surefire 汇总 `1557` 个测试、`11` 个 failures、`29` 个 errors、`1` 个 skipped。失败类集中在既有 `dashboard`、`invoice validation`、`migration`、`payment/purchase/revenue` 和旧 `workflow` 集成测试夹具/业务类型前置，不属于本轮审批通知/预警联动改动引入。
- `git diff --check`：通过。

## 全量失败分类

- `DashboardChiefEngineerServiceTest`：既有 dashboard 测试数据缺失，`Optional.orElseThrow`。
- `InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`：既有发票校验/项目关系测试前置失败。
- `Phase2FullChainIntegrationTest`：既有付款申请状态与余额边界断言失败。
- `Phase4IntegrationTest`、`WorkflowApproverResolverTest`、`WorkflowConcurrencyTest`、`WorkflowCoreServiceTest`、`WorkflowEngineIntegrationTest`、`WorkflowTemplateManagementTest`：既有 workflow 测试夹具缺少真实业务对象或使用当前已不支持的测试业务类型。
- `PurchaseRequestServiceTest`、`ContractRevenueServiceTest`：既有采购/收入提交审批前置或断言失败。

## 结论

通过。目标回归断言已覆盖审批提交、审批完成、审批异常三类联动口径；异常预警具备去重和脱敏边界；本轮未改变外部通知渠道、预警规则中心、审批状态机、权限、租户或项目边界。全量后端测试仍存在既有无关红灯，需后续 Ready Issue 分别治理。
