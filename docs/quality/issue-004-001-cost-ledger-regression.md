# ISSUE-004-001 成本台账与汇总口径回归报告

日期：2026-07-08
结论：通过
阻塞：非阻塞

## 范围

- 成本台账：`CostLedgerService#getSummary` 的总金额、税额、来源类型、项目、成本类型聚合口径。
- 成本汇总：`CostSummaryService#refreshSummary` / `getProjectSummary` 的项目级与科目级目标成本、合同锁定、实际成本、已付款、动态成本、偏差口径。
- Dashboard 成本视图：`DashboardCostServiceTest` 覆盖 `cost_summary` 快照、科目排行、台账行与待付款展示。

## 口径核对

- 台账汇总以 `cost_item.amount` 求和为 `totalAmount`，以 `tax_amount` 求和为 `totalTaxAmount`，并按 `source_type`、`project_id`、`cost_type` 分组。
- 项目汇总中 `actualCost` 汇总实际成本来源，`dynamicCost = actualCost + estimatedRemainingCost`，`costDeviation = dynamicCost - targetCost`。
- `paidAmount` 是项目级口径，同一项目下多科目行不重复累加；现有回归用 2 个科目 + 2 条成功付款断言项目级已付款为 `25000.00`。
- 科目联动中 `costSubjectId` 维度汇总实际成本；签证 `VAR_ORDER` 与合同变更 `CT_CHANGE` 已覆盖同一科目合计 `70000.00`。

## 测试证据

- `CostLedgerServiceTest#getSummaryWithData`：两条台账金额 `100.00 + 200.00 = 300.00`，税额 `10.00 + 20.00 = 30.00`，并验证 `MATERIAL`、`LABOR` 成本类型分组存在。
- `CostLedgerServiceTest#getSummaryEmptyResult` / `getSummaryWithNullAmounts`：空结果与 null 金额返回零值，避免汇总异常。
- `CostLedgerServiceTest#getSummaryDateRangeFilter`：日期范围过滤后金额为 `50.00`。
- `CostSummaryServiceTest#testPaidAmountNotMultipliedBySubjectCount`：项目级 `paidAmount` 不随科目数倍增。
- `CostSummaryServiceTest#testRefreshSummaryIncludesVariationAndContractChangeCosts`：签证与合同变更来源计入项目级和科目级实际成本。
- `DashboardCostServiceTest#testCostView_ReturnsDashboardContractLists`：dashboard 成本视图返回 `targetCost=8000000.00`、`dynamicCost=8200000.00`、科目排行 `actualCost=1500000.00`、待付款 `230000.00`。
- `DashboardCostServiceTest#testCostView_SelectedMonthUsesMonthlySnapshot`：按月份选择时读取截至该月的成本快照。

## 验证命令

原 Ready Issue 命令中的 `CostServiceTest` 不存在；等价最小命令调整为实际覆盖类：

```powershell
cd backend
.\mvnw.cmd "-Dtest=DashboardServiceTest,DashboardCostServiceTest,CostSummaryServiceTest,CostLedgerServiceTest" test
```

结果：通过，`Tests run: 44, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

## 变更判断

本轮未发现真实口径缺陷；现有测试已覆盖成本台账金额/税额聚合、成本汇总项目级与科目级金额、Dashboard 成本视图主链路。因此不新增无意义生产代码或重复测试，仅归档回归证据并更新 backlog 状态。

## 剩余风险

- 本轮未连接生产环境、未跑全量后端测试；结论仅覆盖 ISSUE-004-001 指定最小回归范围。
- `CostServiceTest` 类名不存在，后续 Ready Issue 若继续引用该类名，应使用真实成本测试类替代。
