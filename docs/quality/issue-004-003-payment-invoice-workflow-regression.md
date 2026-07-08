# ISSUE-004-003 付款发票审批状态链路回归报告

日期：2026-07-08

## 目标

验证付款申请、付款写回、发票登记、审批状态三者在主链路上保持一致：

- 付款申请未审批通过时不得付款。
- 审批通过后付款状态可从已批未付进入部分付款或已付款。
- 发票登记必须关联有效付款记录，缺失或无效关联要返回明确业务错误。
- 审批提交链路保留重复提交与业务元数据校验。

## 状态矩阵

| 场景 | approvalStatus | payStatus | 预期结果 | 覆盖证据 |
| --- | --- | --- | --- | --- |
| 新建付款申请 | DRAFT | PENDING | 可编辑、可提交审批；不可直接付款 | `PayApplicationServiceTest` 默认状态断言 |
| 审批中 | APPROVING | PENDING | 不可编辑、不可重复提交 | `testSubmitForApproval_NonDraftNotAllowed` |
| 审批通过且未付款 | APPROVED | APPROVED | 允许付款写回 | `PaymentWritebackTest`、`PaymentFinancialConsistencyTest` |
| 审批驳回 | REJECTED | PENDING | 禁止付款写回，返回 `PAY_APP_NOT_APPROVED` | `testWriteback_RejectedApplicationNotAllowed` |
| 部分付款 | APPROVED | PARTIALLY_PAID | 实付金额累加，不得超过剩余可付金额 | `PayApplicationServiceTest`、`PaymentWritebackTest` |
| 全额付款 | APPROVED | PAID | 全额后拒绝继续超付 | `PayApplicationServiceTest`、`PaymentWritebackTest` |
| 发票缺少付款记录 | 不适用 | 不适用 | 创建/登记失败，返回 `MISSING_PAY_RECORD_ID` | `InvoiceServiceTest` |
| 发票关联无效付款记录 | 不适用 | 不适用 | 创建失败，返回 `PAY_RECORD_NOT_FOUND` | `InvoiceServiceTest` |

## 实现摘要

- 在 `PayRecordService.writeback` 入口增加付款申请审批状态门禁：仅 `approvalStatus=APPROVED` 允许付款写回。
- 在 `PayApplicationServiceTest` 新增驳回付款申请不可付款的回归断言，先复现失败，再验证修复。
- 未修改前端、accounting、migration、deploy 或 AutoPilot 配置。

## 验证命令

- `cd backend; .\mvnw.cmd "-Dtest=PayApplicationServiceTest#testWriteback_RejectedApplicationNotAllowed" test`：通过，`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
- `cd backend; .\mvnw.cmd "-Dtest=PayApplicationServiceTest,InvoiceServiceTest,WorkflowSubmitServiceTest" test`：通过，`Tests run: 53, Failures: 0, Errors: 0, Skipped: 0`。
- `cd backend; .\mvnw.cmd "-Dtest=PaymentWritebackTest,PaymentFinancialConsistencyTest" test`：通过，`Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`。
- `git diff --check`：通过，仅有 CRLF 提示。

## 结论

通过。付款、发票、审批状态链路的本轮回归闭环成立；剩余风险限于未执行全量后端测试。
