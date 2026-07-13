# ISSUE-004-009 付款审批与财务回写状态同步回归

日期：2026-07-09

## 结论

结论：通过  
阻塞：非阻塞  
失败分类或非失败分类：真实代码质量问题已修复；测试夹具问题已更正；全量测试存在既有无关失败

## 目标与范围

本轮回归付款申请从审批通过到财务回写的状态同步口径，覆盖审批通过、审批驳回和财务回写后的状态一致性。修改范围限定在 `payment` / `workflow` 后端代码与测试，以及 backlog / iteration / quality 文档；未修改 migration、deploy、生产凭据、外部财务系统配置或审批状态机定义。

## 修改摘要

- `backend/src/main/java/com/cgcpms/payment/handler/PayRequestWorkflowHandler.java`：审批通过时同步 `approvedAmount = applyAmount`，并保持 `approvalStatus=APPROVED`、`payStatus=APPROVED` 的已批未付口径。
- `backend/src/test/java/com/cgcpms/payment/handler/PayRequestWorkflowHandlerTest.java`：补充审批通过后的付款状态与审批金额同步断言，补充驳回后不误置付款状态断言。
- `backend/src/test/java/com/cgcpms/payment/PaymentWritebackTest.java`：补充财务回写后 `PayRecord.payStatus=SUCCESS`、付款申请审批状态不回退、实付金额合计同步断言。
- `backend/src/test/java/com/cgcpms/payment/PayRecordControllerTest.java`：修正控制器测试夹具，显式模拟审批通过后再执行财务回写，避免测试绕过业务前置。

## 状态同步口径

- 审批通过：`approvalStatus=APPROVED`，`payStatus=APPROVED`，`approvedAmount=applyAmount`；此时代表已批未付，不生成付款记录，不视为财务已付款。
- 审批驳回：`approvalStatus=REJECTED`，既有 `payStatus=PENDING` 不被误置为 `APPROVED`、`PARTIALLY_PAID` 或 `PAID`。
- 财务回写：仅允许 `approvalStatus=APPROVED` 的付款申请写回；成功写回生成 `PayRecord.payStatus=SUCCESS`，并由 `updatePayStatus` 汇总 `SUCCESS` 付款记录同步 `actualPayAmount` 与 `payStatus=PARTIALLY_PAID/PAID`。

## 权限 / 租户 / 项目边界

- 未放宽付款记录控制器的 `payment:record:writeback` 鉴权要求。
- 未放宽 `UserContext.getCurrentTenantId()` 租户过滤和 `selectByIdForUpdate(payApplicationId, tenantId)` 回写锁定口径。
- 未修改项目访问检查、审批状态机定义或外部财务集成入口。

## 验收证据

- `cd backend; .\mvnw.cmd "-Dtest=PayRequestWorkflowHandlerTest#testOnApproved" test`：先失败，失败原因是审批通过后 `approvedAmount` 未同步为申请金额。
- `cd backend; .\mvnw.cmd "-Dtest=PayRequestWorkflowHandlerTest,PaymentWritebackTest,PayApplicationServiceTest,PayRecordControllerTest" test`：通过，`62` 个用例通过。
- `cd backend; .\mvnw.cmd test`：未通过；最终 Surefire 汇总 `1554` 个测试、`11` 个 failures、`29` 个 errors、`1` 个 skipped。失败类集中在既有 `dashboard`、`invoice validation`、`migration`、`purchase`、`revenue`、`workflow` 和跨阶段集成测试；本轮修复后不再包含 `PayRecordControllerTest`。
- `git diff --check`：通过，仅输出工作区换行符转换提示。

## 剩余风险

- 全量后端测试仍存在既有无关红灯，需要后续 Ready Issue 分别治理。
- 本轮不接入真实外部财务系统，不验证生产财务回写通道；结论基于本地服务层、控制器和 H2/local 测试。
- 本轮不修改审批状态机定义，审批流程节点和通知联动仍按既有实现运行。
