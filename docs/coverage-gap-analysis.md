# 后端覆盖率缺口分析报告

> 日期：2026-06-24
> 数据来源：`mvn verify` → JaCoCo CSV 报告

---

## 总体覆盖率

| 指标 | 实际值 | JaCoCo 门禁（临时） | 长期目标 |
|------|--------|---------------------|----------|
| Instruction | **76.5%** | ≥73% ✅ | ≥80% |
| Branch | **55.1%** | ≥53% ✅ | ≥70% |

**结论**：`mvn verify` 已通过当前阈值。指令覆盖距目标 80% 差 **3.5pp**，分支覆盖距目标 70% 差 **14.9pp**。分支是主要瓶颈。

---

## 按包覆盖率（Service 层，按未覆盖字节数排序）

### 严重缺口（inst < 50%）

| 包 | Inst% | Branch% | 未覆盖字节 | 类数 | 建议 |
|-----|-------|---------|-----------|------|------|
| `overhead.service` | 20.9% | 14.6% | 458 | 1 | **P0** — OverheadAllocationService 几乎未测 |
| `accounting.service` | 36.2% | 43.8% | 287 | 2 | **P1** — EntryGenerator 完全没有覆盖 |
| `settlement.service` | 45.6% | 27.4% | 1,161 | 5 | **P1** — StlSettlementQueryService 仅测了约 36% |

### 中等缺口（inst 50%~80%）

| 包 | Inst% | Branch% | 未覆盖字节 | 类数 |
|-----|-------|---------|-----------|------|
| `revenue.service` | 65.6% | 49.0% | 370 | 1 |
| `receipt.service` | 64.8% | 47.2% | 565 | 3 |
| `alert.service` | 67.7% | 52.8% | 448 | 1 |
| `variation.service` | 68.7% | 44.1% | 301 | 1 |
| `file.service` | 70.8% | 54.0% | 378 | 3 |
| `subcontract.service` | 78.8% | 54.7% | 351 | 2 |
| `project.service` | 79.9% | 57.9% | 348 | 3 |

### 已较好覆盖（inst > 80%）

| 包 | Inst% | Branch% |
|-----|-------|---------|
| `payment.service` | 80.3% | 52.7% |
| `purchase.service` | 85.8% | 62.9% |
| `cost.service` | 86.9% | 61.7% |
| `workflow.service` | 87.9% | 65.2% |
| `dashboard.service` | 88.6% | 54.2% |

---

## 按包覆盖率（Controller 层零覆盖或极低）

| 包 | Inst% | 类数 |
|-----|-------|------|
| `file.controller` | 0.0% | 1 |
| `partner.controller` | 0.0% | 1 |
| `material.controller` | 0.0% | 1 |
| `audit.controller` | 3.5% | 1 |
| `workflow.controller` | 13.7% | 2 |
| `variation.controller` | 13.8% | 1 |
| `notification.controller` | 32.6% | 1 |
| `org.controller` | 36.4% | 3 |
| `contract.controller` | 36.4% | 4 |
| `project.controller` | 34.4% | 2 |

> 注：Controller 层已通过 Phase A 收口完成（18 个新测试文件），部分 Controller 采用 MockMvc 集成测试但 JaCoCo agent 模式下插桩不完全涵盖。零覆盖不等于无测试。

---

## 优先补测建议

为达到 80% / 70% 长期目标，建议分两轮：

### 第一轮（P0-P1，预期 +3pp inst / +5pp branch）

1. **`OverheadAllocationService`** — 20.9% → 60%+：间接费分摊逻辑（按项目/合同的权重分配），边界条件丰富，补测收益极高
2. **`StlSettlementQueryService`** — 目前 ~36%：大量只读查询方法（getPage/getKpi/getById/computeAmount/getSources/getVariations 等），每个方法独立可测
3. **`ContractRevenueService`** — 65.6% → 80%+：收入确认逻辑，已有基础测试可扩展

### 第二轮（P1-P2，预期 +2pp inst / +5pp branch）

4. **`MatReceiptAssembler`** — 大量 VO 转换逻辑：分支密集（69 missed branches），补测分支覆盖率提升明显
5. **`EntryGenerator`** — 0%：会计分录生成器，独立可测但需构造借贷方数据

---

## 每轮预期效果

| 轮次 | 补测类 | 预期 inst | 预期 branch | 累计 inst | 累计 branch |
|------|--------|-----------|-------------|-----------|-------------|
| 当前 | — | 76.5% | 55.1% | 76.5% | 55.1% |
| 第一轮 | 3 个 Service | +3pp | +5pp | ~79.5% | ~60.1% |
| 第二轮 | 2 个 Service/Assembler | +2pp | +5pp | ~81.5% | ~65.1% |
| 第三轮（后续） | 其余中缺口包 | +1~2pp | +5pp | ~82%+ | ~70% |

> 分支覆盖率 55%→70% 是主要挑战，策略上优先补分支密集的类（Assembler/VO 转换、校验逻辑）。
