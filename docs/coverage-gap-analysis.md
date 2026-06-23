# 后端覆盖率缺口分析报告

> 日期：2026-06-24
> 数据来源：`mvn verify` → JaCoCo CSV 报告
> 总测试数：**1,235** tests, **0 failures, 0 errors**

---

## 总体覆盖率

| 指标 | 初始值 (06-23) | 当前值 (06-24) | JaCoCo 门禁（临时） | 长期目标 |
|------|---------------|---------------|---------------------|----------|
| Instruction | **76.5%** | _待重跑_ | ≥73% ✅ | ≥80% |
| Branch | **55.1%** | _待重跑_ | ≥53% ✅ | ≥70% |

---

## 本轮已完成

| # | 类 | 测试数 | 状态 |
|---|-----|--------|------|
| 1 | `OverheadAllocationServiceTest` | 7→14 (+7) | ✅ 全量 1235 PASS |
| 2 | `StlSettlementQueryServiceTest` | 0→26 (新增) | ✅ 全量 1235 PASS |
| 3 | `ContractRevenueServiceTest` | 原有 10 个 | ✅ 已有基础覆盖 (65.6%→?) |

### 覆盖详情

**OverheadAllocationServiceTest** — 覆盖：
- CRUD：create 默认 ENABLE、getPage 分页/租户过滤、update 成功/不存在/租户隔离、delete 成功/不存在
- executeAllocation 边界：无规则、无活跃项目、零金额科目、非月度周期、DISABLE 状态
- scheduledMonthlyAllocation 并发守卫
- 唯一 subjectId 避免与 TenantBoundaryTask2Test 表索引冲突

**StlSettlementQueryServiceTest** — 覆盖：
- getPage × 6（全量/按项目/按合同/按类型-字段映射/编号模糊搜索/关键字无结果）
- getKpi × 3（全量/按项目/无数据）
- getById × 3（存在/不存在/租户不匹配）
- computeSettlementAmount × 2（有效合同/不存在）
- getSources × 2（存在/不存在）
- 关联查询 × 10（getVariations/getPayments/getCosts/getAttachments/getApprovalRecords × happy + not-found）

---

## 后续优先建议

### 第一轮（P1-P2）

1. **`EntryGenerator`** — 0% 覆盖，会计分录生成器，独立可测
2. **`MatReceiptAssembler`** — 分支密集（69 missed branches），补测分支覆盖率提升明显
3. **`ContractRevenueService`** — 65.6%→80%+ 扩展，已有 10 个测试为基础

### 第二轮（P2）

4. Controller 零覆盖/低覆盖层（file/partner/material/audit controller）
5. 其余 Service 中缺口包（receipt/alert/variation 等）
