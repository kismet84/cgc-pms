# 成本科目 V2 Phase 0 本地数据审计报告

状态：不通过进入 Phase 1；本地基线审计完成，等待目标环境数据核验。

## 1. 审计目标与边界

本报告核验第 51 条主线在本地可访问数据库和当前源码中的成本科目结构、引用、约束与迁移前风险。审计对象仅为 Docker 本地 MySQL 容器内的 `cgc_pms` 与 `cgc_pms_restore_test`；没有连接、读取或推断生产数据库。

本报告不执行数据更新、迁移、修复、删除、Flyway 操作或业务单据写入。

## 2. 审计证据

| 证据         | 当前事实                                                                                         |
| ------------ | ------------------------------------------------------------------------------------------------ |
| 当前开发库   | `cgc_pms`，Flyway 最新成功版本 V212。                                                            |
| 本地恢复快照 | `cgc_pms_restore_test`，Flyway 最新成功版本 V145。                                               |
| 现行科目模型 | `CostSubject` 同时包含 `accountCategory`、`subjectType`、`parentId`、`level` 与 `status`。       |
| 动态利润控制 | 预测创建要求“目标成本与已发生科目完整覆盖”，并拒绝未归类非零成本。                               |
| 默认归集     | `CostSubjectResolver` 在类型未命中时会回退根节点，再回退任一启用科目。                           |
| 删除保护     | 服务层显式检查 `cost_item`、`cost_target_item`；开发库另有预算、付款、费用、预测、会计凭证外键。 |

源码依据：

- `backend/src/main/java/com/cgcpms/cost/entity/CostSubject.java`
- `backend/src/main/java/com/cgcpms/cost/service/CostControlService.java`
- `backend/src/main/java/com/cgcpms/cost/strategy/CostSubjectResolver.java`
- `backend/src/main/java/com/cgcpms/cost/service/CostSubjectService.java`

## 3. 数据库基线

### 3.1 `cgc_pms`（当前开发库）

| 指标                 |                                            结果 |
| -------------------- | ----------------------------------------------: |
| 未逻辑删除科目       |                                             114 |
| 启用科目             |                                             111 |
| 停用科目             |                                               3 |
| 成本科目             |                                              87 |
| 收入科目             |                                              19 |
| 应收科目             |                                               2 |
| 结算科目             |                                               5 |
| 逻辑删除前的重复编码 |                                               0 |
| 同名有效成本科目     | 1 组：`5001.03`、`COST_MATERIAL` 均为“材料成本” |

科目引用快照：

| 引用表                  | 记录数 | 已填 `cost_subject_id` | 未归类记录 |
| ----------------------- | -----: | ---------------------: | ---------: |
| `cost_item`             |      1 |                      1 |          0 |
| `cost_target_item`      |      0 |                      0 |          0 |
| `cost_forecast_item`    |      0 |                      0 |          0 |
| `project_budget_line`   |      0 |                      0 |          0 |
| `pay_application`       |      2 |                      0 |          2 |
| `expense_application`   |      0 |                      0 |          0 |
| `accounting_entry_line` |      0 |                      0 |          0 |
| `stl_settlement_item`   |      2 |                      0 |          2 |

结论：开发库只覆盖极少量成本事实，且付款、结算均未填成本科目。它可用于验证迁移脚本语法和空数据边界，不能证明历史映射、投标成本转入、质量安全归集、财务费用分摊或对账正确性。

### 3.2 `cgc_pms_restore_test`（本地恢复快照）

| 指标                                                               |                                    结果 |
| ------------------------------------------------------------------ | --------------------------------------: |
| 未逻辑删除科目                                                     |                                     114 |
| 启用科目                                                           |                                     114 |
| 成本科目                                                           |                                      87 |
| 收入科目                                                           |                                      19 |
| 应收科目                                                           |                                       2 |
| 结算科目                                                           |                                       5 |
| 重复启用编码                                                       | 2 组：`REVENUE/6001`、`REVENUE/6001.01` |
| `cost_item` 已归类记录                                             |                                       1 |
| `cost_target_item`、`accounting_entry_line`、`stl_settlement_item` |                                均无引用 |

重复收入编码的两套来源分别为旧收入树（`900002`、`900007`）和标准收入树（`900200`、`900201`）。该快照停留在 V145，且缺少当前开发库中已存在的 `cost_forecast_item`、`project_budget_line`、`pay_application`、`expense_application` 的科目列。

结论：该库为历史恢复快照，不是当前 schema，也不能作为 Phase 1 数据迁移演练环境。

## 4. 引用与完整性风险

### 4.1 当前开发库外键

`cost_subject.id` 当前被以下外键引用：

- `accounting_entry_line.cost_subject_id`
- `cost_forecast_item.cost_subject_id`
- `cost_target_item.cost_subject_id`
- `expense_application.cost_subject_id`
- `pay_application.cost_subject_id`
- `project_budget_line.cost_subject_id`

`cost_item.cost_subject_id` 与 `stl_settlement_item.cost_subject_id` 在当前开发库中没有指向 `cost_subject` 的外键；它们必须保留应用层引用保护和迁移对账。

### 4.2 服务层风险

- `CostSubjectService.delete` 只主动检查成本明细和目标成本明细。虽然部分表由数据库外键保护，但结算明细未受上述外键约束，且停用操作没有引用影响分析。
- `CostSubjectResolver` 的“根节点/任意启用科目”回退会让 `subject_type` 不一致转化为错误成本归集，而不是可见的待归类问题。
- `CostControlService` 已将未归类成本视为预测阻断条件；因此 V2 必须把归集失败前移到单据提交或过账前。

## 5. Phase 0 本地结论

| 检查项           | 结论   | 依据                                                   |
| ---------------- | ------ | ------------------------------------------------------ |
| V2 逻辑科目设计  | 可继续 | 现行树、代码引用与用户确认的业务规则已形成计划基线。   |
| 历史数据映射     | 不通过 | 当前 dev 数据量不足；restore 快照旧且有重复编码。      |
| 历史金额对账     | 不通过 | 目标成本、预测、预算、费用、凭证均无可用引用样本。     |
| 生产迁移演练     | 不通过 | 未获得生产或等价目标环境的只读快照。                   |
| Phase 1 代码实施 | 阻塞   | 第 51 条计划明确要求先完成完整引用扫描与财务口径确认。 |

## 6. 阻塞条件与解除标准

| 阻塞                   | 解除条件                                                 | 验收证据                                               |
| ---------------------- | -------------------------------------------------------- | ------------------------------------------------------ |
| 缺少目标环境引用数据   | 提供经授权的生产只读导出或等价、同版本脱敏快照           | 各引用表按租户、科目、状态、金额、期间的统计清单。     |
| 历史映射未获确认       | 成本、商务、采购、质量安全共同确认第 51 条计划的映射矩阵 | 带版本号、审批人和生效日期的映射清单。                 |
| 财务费用分摊口径未落地 | 财务确认可用分摊依据和会计映射                           | 分摊规则、审批边界、幂等/冲销规则和测试用例。          |
| 投标成本转入核对不足   | 明确中标、转入、冲销和重复转入边界                       | 至少一组完整样本的来源、目标成本版本、审批与对账用例。 |

### 6.1 阻塞处理进展（2026-07-18）

1. 已新增 [`cost-subject-v2-phase0-target-audit.sql`](../../scripts/database/cost-subject-v2-phase0-target-audit.sql)，仅含 `SELECT`，用于在获授权的生产只读副本或同版本脱敏快照采集解除条件一中的完整证据。当前工作站未发现同版本可用导出，脚本不能替代目标环境数据。
2. 已核对 `BidCostService.markAsWon`：它将既有 `cost_item` 的 `source_type` 从 `BID_COST` 原地更新为 `BID_COST_TRANSFERRED`，并写入 `project_id`；实际执行 `backend\\mvnw.cmd -Dtest=BidCostServiceTest test`，12 项通过，覆盖中标后的单次结转和重复中标阻断。它**不**创建或关联 `cost_target`、`cost_target_item`、目标成本版本、审批实例或反向转入事实。因此“投标费用进入中标项目目标成本”已从待核对项收敛为明确的 Phase 2 实现缺口，不能把现有实际成本结转误判为目标成本转入。
3. 现有 `OverheadAllocationService` 仅接受 `subject_type=OVERHEAD` 且 `account_category=COST` 的科目，规则基础只有 `DIRECT_LABOR`、`CONTRACT_AMOUNT`、`USAGE`，分摊结果写为 `OVERHEAD_ALLOCATION` 成本事实。它不能直接承载项目财务费用分摊；财务仍须确认来源会计事实、可用依据和会计映射。该项继续阻塞。

## 7. 裁决

第 51 条主线的 Phase 0 本地数据基线审计完成，但**不通过进入 Phase 1**。阻塞属于数据与业务前置，不是代码实现失败。已提供只读采集入口，并将投标成本转入的现有能力边界核定为 Phase 2 实现缺口；继续实施前仍必须满足第 6 节全部解除条件。

后续项：无新增独立后续项；上述阻塞由第 51 条主线计划第 10、11 节唯一承接。
