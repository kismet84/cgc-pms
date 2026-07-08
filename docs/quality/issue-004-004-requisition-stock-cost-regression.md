# ISSUE-004-004 领料出库与项目成本归集回归报告

日期：2026-07-08

## 目标

验证领料审批通过后的主链路一致性：

- 审批通过后领料单进入 `APPROVED`，并写入 `stockOutFlag=1`。
- 出库流水带 `sourceType=MAT_REQUISITION` 和本次领料单 `sourceId`，可回溯来源单据。
- 库存可用数量按本次领料数量扣减，且流水 `availableAfter` 与库存余额一致。
- 项目成本归集生成 `MATERIAL` / `CONFIRMED` 成本项，金额、项目、合同与领料单据一致。

## 回归证据

| 证据类型 | 断言位置 | 断言摘要 |
| --- | --- | --- |
| 状态字段 | `MatRequisitionWorkflowSubmitTest#approvedRequisitionCreatesStockOutLedger` | `approvalStatus=APPROVED`、`stockOutFlag=1` |
| 出库流水 | `MatRequisitionWorkflowSubmitTest#approvedRequisitionCreatesStockOutLedger` | `txnType=OUT`、`sourceType=MAT_REQUISITION`、`sourceId=requisitionId`、`quantity=8.00` |
| 库存扣减 | `MatRequisitionWorkflowSubmitTest#approvedRequisitionCreatesStockOutLedger` | 初始 `availableQty=20.00`，审批通过后库存与流水余额均为 `12.00` |
| 成本归集 | `MatRequisitionWorkflowSubmitTest#approvedRequisitionCreatesStockOutLedger` | `projectId=10001`、`contractId=30001`、`costType=MATERIAL`、`costStatus=CONFIRMED`、`amount=100.00` |
| 台账查询 | `CostLedgerServiceTest#getPageIncludesMaterialRequisitionSeedForTargetProject` | 目标项目存在 `MAT_REQUISITION` 成本台账行 |

## 修改摘要

- 本轮未修改后端生产代码。
- 本轮未新增测试代码；现有 `MatRequisitionWorkflowSubmitTest` 已覆盖 ISSUE-004-004 要求的状态、库存流水、库存扣减和成本归集断言。
- 本轮仅新增正式质量报告，并更新 backlog / iteration 状态。

## 验证命令

- `cd backend; .\mvnw.cmd "-Dtest=MatRequisitionWorkflowSubmitTest,MatStockServiceTest,CostLedgerServiceTest" test`：通过，`Tests run: 49, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

## 结论

通过。领料审批通过后的出库流水、库存扣减、项目成本归集三类证据闭环成立。

## 剩余风险

- 本轮未跑全量后端测试，结论限于 ISSUE-004-004 指定回归范围。
- 领料成本科目仍沿用既有默认材料科目解析逻辑，本轮不扩展成本科目配置能力。
