# 全量代码审查修复计划

基于：`doc/全量代码审查报告_2026-06-11.md`
日期：2026-06-11

---

## TODOs

### Fix 1: 成本接口重复 `/api` 前缀
- [x] F1: 修复 `CostLedgerController.java:17` `@RequestMapping("/api/cost-ledger")` → `/cost-ledger`
- [x] F2: 修复 `CostSubjectController.java:16` `@RequestMapping("/api/cost-subjects")` → `/cost-subjects`

### Fix 2: 动态成本汇总 & 付款依据契约对齐
- [x] F3: 后端 `CostSummaryController` GET `/{projectId}` 返回项目级聚合对象 `CostProjectSummaryVO`（含 projectId, totalContractCost, totalActualCost, totalPaidAmount, subjects: List<CostSummaryVO>）
- [x] F4: 后端 `CostSummaryController` POST `/{projectId}/refresh` 改为返回 `CostProjectSummaryVO`
- [x] F5: 前端 `cost.ts` `getCostSummary`/`refreshCostSummary` 适配新契约类型 `CostProjectSummaryVO`
- [x] F6: 前端 `summary.vue` 适配新数据结构
- [x] F7: 后端 `PayApplicationController` `/{id}/basis` 改为返回 `List<PayApplicationBasisVO>`
- [x] F8: 前端 `payment.ts` `getBasisList` 确认类型对齐

### Fix 3: 材料验收明细重复保存累加修复
- [x] F9: `MatReceiptService.saveItemsBatch()` 删除旧明细前先扣回旧 quantity，再累加新 quantity
- [x] F10: 添加回归测试：同一验收单保存两次，采购订单 receivedQuantity 不应重复增加

### Fix 4: 付款审批改为关键处理
- [x] F11: `PayRequestWorkflowHandler.isCritical()` 改为 `return true`

### Fix 5: 已审批/已生成成本的单据补充编辑/删除守卫
- [x] F12: `MatReceiptService` update/delete/saveItemsBatch 增加 approvalStatus 和 costGeneratedFlag 校验
- [x] F13: `SubMeasureService` update/delete 增加 approvalStatus 和 costGeneratedFlag 校验
- [x] F14: `VarOrderService` update/delete 增加 approvalStatus 和 costGeneratedFlag 校验
- [x] F15: `PurchaseOrderService` update/delete 增加 approvalStatus 和 costGeneratedFlag 校验
- [x] F16: `PayApplicationService` update/delete 增加 approvalStatus 校验
- [x] F17: 添加回归测试：审批通过后修改明细应被拒绝

### Fix 6: 成本汇总定时任务租户上下文
- [x] F18: `CostSummaryService.scheduledRefresh()` 从 project 查询 tenantId 并显式传入
- [x] F19: `CostSummaryService.refreshSummary()` 接受显式 tenantId 参数
- [x] F20: `CostSummaryService.updatePaidAmount()` 增加 tenantId 条件

### Fix 7: 后端集成测试可重复运行
- [x] F21: `WorkflowEngineIntegrationTest` 使用 H2 profile，生成唯一 businessId，@BeforeEach 清理测试数据

---

## Final Verification Wave

### F1: Oracle Review — 目的与约束验证
- [x] Oracle 审查：修复是否解决了报告指出的根本问题？是否有遗漏？ → **APPROVE**

### F2: Oracle Review — 代码质量
- [x] Oracle 审查：代码逻辑正确性、边界情况、事务一致性 → **APPROVE** (issues fixed: PurchaseOrder guard narrowed, tenantId leak patched)

### F3: Oracle Review — 安全审查
- [x] Oracle 审查：新增守卫是否有效，租户隔离是否正确 → **APPROVE** (issue fixed: PayApplication field reset added)

### F4: Hands-On QA
- [x] 运行 `.\mvnw.cmd test` → **26/26 PASS, 0 failures, 0 errors**
- [x] 前端 `pnpm type-check` 通过
- [x] 前端 `pnpm build` 通过
- [x] 手动验证：确认所有修复的代码逻辑正确
