# ISSUE-004-005 分包计量与结算状态链路回归报告

日期：2026-07-08

## 目标

验证分包计量、结算生成、审批通过后的状态流转与金额口径：

- 分包计量创建、提交审批、撤回后重提和已审批保护条件保持稳定。
- 结算生成、结算金额、来源数据和付款关联结果可解释。
- 结算审批通过、驳回、撤回后的状态写回符合既有口径。
- 审批通过后避免误编辑或误删除导致链路回退。

## 回归证据

| 证据类型 | 断言位置 | 断言摘要 |
| --- | --- | --- |
| 分包计量提交 | `SubMeasureServiceTest#testSubmitForApproval` | `DRAFT -> APPROVING`，审批中心生成待办 |
| 分包计量重提 | `SubMeasureServiceTest#testSubmitForApproval_WithdrawnDraftResubmits` | 已撤回实例重新进入 `RUNNING`，轮次与重提次数递增 |
| 分包计量保护 | `SubMeasureServiceTest#testUpdate_WhenApproved` | `APPROVED` 计量禁止继续编辑 |
| 结算创建 | `StlSettlementServiceTest#shouldCreateSettlement` | 结算创建后字段与金额保持一致 |
| 结算审批通过 | `SettlementWorkflowHandlerTest#testOnApproved_Success` | `APPROVING -> APPROVED`，`DRAFT -> FINALIZED` |
| 结算驳回/撤回 | `SettlementWorkflowHandlerTest#testOnRejected`、`testOnWithdrawn` | 驳回写 `REJECTED`，撤回恢复 `DRAFT` |
| 来源与付款 | `StlSettlementQueryServiceTest#testGetSources`、`testGetPayments` | 来源列表、付款状态归一化、申请/审批/实付金额可解释 |
| 成本关联 | `StlSettlementQueryServiceTest#testGetCosts` | 成本项金额、科目、日期和来源记录可查询 |

## 修改摘要

- 本轮未修改后端生产代码。
- 本轮未新增测试代码；现有四个测试类已覆盖 ISSUE-004-005 的状态、金额、来源、付款和保护条件。
- 本轮仅新增正式质量报告，并更新 backlog / iteration 状态。

## 验证命令

- `cd backend; .\mvnw.cmd "-Dtest=SubMeasureServiceTest,StlSettlementServiceTest,SettlementWorkflowHandlerTest,StlSettlementQueryServiceTest" test`：通过，`Tests run: 62, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

## 结论

通过。分包计量提交、结算生成、审批状态写回、来源和付款关联在本轮指定回归范围内闭环成立。

## 剩余风险

- 本轮未跑全量后端测试，结论限于 ISSUE-004-005 指定回归范围。
- 本轮未新增浏览器或前端验收，结算页面交互不属于该 Ready Issue 的允许范围。
