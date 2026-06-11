# W0 决策记录

## 决策 1：付款表命名

**时间**：2026-06-11
**决策者**：Atlas（按计划推荐方向裁定）

**决策：A — 沿用 V4 的 `pay_application`**

**理由**：
- V4 migration 已建 `pay_application` 表，避免改已建表
- 设计文档 05 使用 `pay_request`，但 migration 是 ground truth
- 代码注释标注别名：`// alias: pay_request (design doc terminology)`
- 注意：`pay_request_basis` 关联表应使用 `pay_application_basis` 以保持命名一致性

---

## 决策 2：成本生成服务形态

**时间**：2026-06-11
**决策者**：Atlas

**决策：B — 策略模式**

**设计**：
- `CostGenerationService` 扩展为策略分发器
- 按 `source_type` 注册策略实现（Map<String, CostGenerationStrategy>）
- 每个策略实现 `generateCost(sourceId)`：查询源单据明细 → 映射为 CostItem → 批量 insert（uk_cost_source_item 幂等兜底）
- 现有 `generateLockedCost(contractId)` 重构为 `CT_CONTRACT` 策略
- 新增 `MAT_RECEIPT`、`SUB_MEASURE`、`VAR_ORDER` 策略

**source_type 映射**：

| source_type | source_id | source_item_id | cost_type | amount 来源 |
|---|---|---|---|---|
| `CT_CONTRACT` | contract.id | contract_item.id | CONTRACT_LOCKED | item.amount |
| `MAT_RECEIPT` | mat_receipt.id | mat_receipt_item.id | MATERIAL | receipt_item.amount |
| `SUB_MEASURE` | sub_measure.id | sub_measure_item.id | SUBCONTRACT | measure_item.amount |
| `VAR_ORDER` | var_order.id | var_order_item.id | VARIATION | var_order_item.amount |

---

## 决策 3：采购→验收数量校验

**时间**：2026-06-11
**决策者**：Atlas

**决策：B — 宽松（超量预警不拦截）**

**理由**：符合文档"超量触发预警"而非禁止。实际工程中可能存在合理超量场景（如损耗、补货）。

**实现**：验收提交审批时计算 `验收量 - 订单量`，>0 时在审批摘要中标注预警信息，但不阻止提交。

---

## 决策 4：签证 direction 收入向处理

**时间**：2026-06-11
**决策者**：Atlas

**决策：B — 本阶段仅做成本向，收入向预留**

**理由**：收入侧涉及合同变更流程，复杂度高，留第 3 阶段。本阶段 `var_order` 的 `direction` 字段预留 `REVENUE` 值，但业务逻辑仅处理 `COST`。

---

## 决策 5：cost_summary 刷新方式

**时间**：2026-06-11
**决策者**：Atlas

**决策：B — 定时 + 手动刷新**

**理由**：
- MVP 阶段避免实时触发的性能与一致性复杂度
- 定时任务（如每小时）自动刷新 + 手动刷新按钮
- `cost_summary` 表字段：target_cost / locked_cost / occurred_cost / paid_amount / variance
- 后续可演进为事件驱动实时更新

---

## 决策 6：付款金额校验策略（Oracle 评审结论）

**时间**：2026-06-11
**评审者**：Oracle
**采纳**：是

**Oracle 核心结论**：

1. **不用策略模式**：Rules 3 和 4 结构相同（仅源表不同），单次付款可混合引用验收单和计量单，统一校验器 + 参数化查询更简洁。

2. **悲观锁（SELECT ... FOR UPDATE）+ READ COMMITTED**：
   - 锁合同行 → 锁所有依据项（按 ID 升序防死锁）→ 校验 → 插入
   - 序列化同一合同/依据项的并发付款，不阻塞无关合同

3. **两阶段校验**：
   - 提交时：建议性校验（不预留预算）→ 告诉用户"当前可通过"
   - 审批时：权威校验（悲观锁内重新校验）→ 实际预留预算
   - 提交中未审批的申请不占预算（防止提交垃圾阻塞）

4. **补充缺失规则**：
   - M1: 付款金额必须等于依据项金额之和（`header.amount == SUM(basis.amount)`）
   - M2: 同一付款不得重复引用同一依据项
   - M3: 依据项必须属于同一合同（`basis_item.contract_id == payment.contract_id`）
   - M4: 付款记录金额不得超过申请金额（`SUM(pay_record.amount) ≤ pay_application.amount`）

5. **校验顺序**：Rule 1（合同余额）→ Rule 2（比例约束）→ Rules 3+4（逐项校验）

> 完整 Oracle 评审输出见 bg_e9735a70 会话记录。关键算法伪代码已记录，D2 任务实现时严格参考。
