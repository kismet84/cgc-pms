# Dimension 5: Business Logic Correctness Review

**Date:** 2026-06-15
**Scope:** Workflow engine, cost calculation, inventory concurrency, settlement locking, warranty calculation, SSE push
**Coverage:** 6 areas across backend services, entities, DB schema, and nginx config

---

## Findings Summary

| ID | Severity | Area | File | Summary |
|----|----------|------|------|---------|
| D5-001 | P0 | Settlement | StlSettlementService.java:162-169 | contractId check-then-insert TOCTOU race - no DB UNIQUE constraint |
| D5-002 | P1 | Settlement | StlSettlementService.java:174-188 | Settlement code sequence TOCTOU race |
| D5-003 | P1 | Settlement | SettlementWorkflowHandler.java:106-118 | Settlement finalization lacks optimistic lock |
| D5-004 | P1 | Cost | MaterialReceiptCostStrategy, SubMeasureCostStrategy, VarOrderCostStrategy | taxAmount/amountWithoutTax not populated |
| D5-005 | P2 | Workflow | WfTask.java:40-41 | taskVersion @Version intact (correct) |
| D5-006 | P2 | Workflow | WorkflowApprovalService.java:47-49,125-127 | Version conflict handling correct |
| D5-007 | P2 | Inventory | MatStockService.java:88-101,122-142 | Stock @Version with retry logic correct |
| D5-008 | P2 | Workflow | WorkflowCoreService.java:293-303 | isCritical() rollback control intact after split |
| D5-009 | P2 | Cost | ContractCostStrategy + all 4 strategies | uk_cost_source_item idempotency correct |
| D5-010 | P2 | Settlement | StlSettlementService.java:54, 314-316, 473-475 | Warranty rate calculation correct |
| D5-011 | P3 | SSE | frontend-admin/nginx.conf:68,150 | proxy_buffering off for SSE both HTTP/HTTPS |
| D5-012 | P3 | Workflow | WorkflowController.java:78 | PURCHASE_REQUEST switch case present |

---

## Detailed Findings

### [D5-001] P0 StlSettlementService.java:162-169 - settlement duplicate TOCTOU race

**File:** backend/src/main/java/com/cgcpms/settlement/service/StlSettlementService.java
**Lines:** 162-169
**Repro:** Two concurrent POST requests to create a settlement for the same contractId. Both pass the existingCount > 0 check (both see 0) and proceed to insert.
**Root Cause:** Check-then-insert pattern not backed by a DB-level UNIQUE constraint on (tenant_id, contract_id). The only unique key on stl_settlement is uk_stl_settlement_code (tenant_id, settlement_code) per V12 migration line 366. No constraint prevents two settlements for the same contract.
`java
// Lines 162-169: TOCTOU race
Long existingCount = stlSettlementMapper.selectCount(
    new LambdaQueryWrapper<StlSettlement>()
        .eq(StlSettlement::getTenantId, tenantId)
        .eq(StlSettlement::getContractId, settlement.getContractId()));
if (existingCount > 0) {
    throw new BusinessException("STL_DUPLICATE_SETTLEMENT", ...);
}
`
**Fix:** Add a DB-level UNIQUE constraint on (tenant_id, contract_id) to stl_settlement and catch DuplicateKeyException in the service.

---

### [D5-002] P1 StlSettlementService.java:174-188 - settlement code sequence TOCTOU race

**File:** backend/src/main/java/com/cgcpms/settlement/service/StlSettlementService.java
**Lines:** 174-188
**Repro:** Concurrent creates at the same millisecond may compute the same sequence number. The DuplicateKeyException from uk_stl_settlement_code is not caught, causing a 500 error.
**Fix:** Wrap in a retry loop on DuplicateKeyException or use a database sequence / Redis INCR for atomic code generation.

---

### [D5-003] P1 SettlementWorkflowHandler.java:106-118 - settlement finalization lacks optimistic lock

**File:** backend/src/main/java/com/cgcpms/settlement/handler/SettlementWorkflowHandler.java
**Lines:** 106-118
**Repro:** If two workflow approvals for the same settlement complete concurrently (countersign race), both onApproved() callbacks could execute. The update uses LambdaUpdateWrapper with only ID guard, no version or status check.
`java
// Lines 106-110: No version/status guard
stlSettlementMapper.update(null, new LambdaUpdateWrapper<StlSettlement>()
    .eq(StlSettlement::getId, settlementId)
    .set(StlSettlement::getSettlementStatus, "FINALIZED")
    .set(StlSettlement::getFinalizedAt, LocalDateTime.now()));
`
**Fix:** Add .eq(StlSettlement::getSettlementStatus, "DRAFT") or use @Version on StlSettlement.

---

### [D5-004] P1 Cost Strategy - taxAmount/amountWithoutTax not populated in 3 of 4 strategies

**Files:**
- MaterialReceiptCostStrategy.java
- SubMeasureCostStrategy.java
- VarOrderCostStrategy.java

**Issue:** Only ContractCostStrategy populates taxAmount and amountWithoutTax on generated CostItem. The other 3 strategies set only amount, leaving tax fields as DB default 0. If source entities carry tax data, it is lost.
**Example (MaterialReceiptCostStrategy.java:78):**
`java
cost.setAmount(nvl(item.getAmount()));
// taxAmount and amountWithoutTax not set -> default 0
`
**Fix:** Either populate tax fields if source entities carry them, or document the intentional omission.

---

### [D5-005] P2 WfTask @Version intact

**File:** WfTask.java:40-41
**Status:** Correct
**Detail:** WfTask entity has @Version on taskVersion (Integer). MyBatis-Plus auto-increments and uses as WHERE clause on updates.

---

### [D5-006] P2 Version conflict handling correct

**File:** WorkflowApprovalService.java:47-49,125-127
**Status:** Correct
**Detail:** Both approve() and reject() check updated == 0 after updateById(task) and throw meaningful TASK_VERSION_CONFLICT.

---

### [D5-007] P2 MatStock @Version with retry correct

**File:** MatStockService.java:88-101,122-142
**Status:** Correct
**Detail:** MatStock entity has @Version on version. Both doUpdateIncrement() (stockIn) and stockOut() implement retry (MAX_RETRIES=3) with findStock() reload. DuplicateKeyException on initial INSERT is handled with fallback to UPDATE.

---

### [D5-008] P2 isCritical() rollback control intact after split

**File:** WorkflowCoreService.java:293-303
**Status:** Correct
**Detail:** After workflow engine split into sub-services, isCritical() check in notifyHandler() still correctly propagates exceptions when true (all 10 handlers return true) and swallows+logs when false. All sub-service entry points have @Transactional.

---

### [D5-009] P2 uk_cost_source_item idempotency correct

**Files:** All 4 strategies + V4 migration
**Status:** Correct
**Detail:** uk_cost_source_item = UNIQUE (source_type, source_id, source_item_id, cost_type). All 4 strategies set all fields and catch DuplicateKeyException for idempotent skip.

---

### [D5-010] P2 Warranty rate calculation correct

**File:** StlSettlementService.java:54, 314-316, 473-475
**Status:** Correct
**Detail:**
- DEFAULT_WARRANTY_RATE = new BigDecimal("0.05") stored as ratio (0.05 = 5%)
- contract.warrantyRate stored as percentage (e.g. 5.00 = 5%), converted via movePointLeft(2) to 0.05
- Formula: warrantyAmount = finalAmount x warrantyRate - correct ratio multiplication
**Escape analysis:** If contract stores 5.00% as value 5.00, movePointLeft(2) -> 0.05, finalAmount x 0.05 = 5% of finalAmount. Correct.

---

### [D5-011] P3 nginx.conf proxy_buffering off for SSE

**File:** frontend-admin/nginx.conf
**Lines:** 68 (HTTP), 150 (HTTPS)
**Status:** Correct
**Detail:** Both /api/ location blocks have proxy_buffering off with proxy_buffer_size 4k and proxy_buffers 8 16k. SSE endpoint at NotificationController.java:108 uses SseEmitter with TEXT_EVENT_STREAM_VALUE.

---

### [D5-012] P3 PURCHASE_REQUEST switch case present

**File:** WorkflowController.java:78
**Status:** Correct
**Detail:** getRequiredPermission() includes: case PURCHASE_REQUEST -> "purchase:request:submit". All 10 business types mapped.

---

## Appendix: Key Files Reviewed

| File | Purpose |
|------|---------|
| backend/.../workflow/service/WorkflowCoreService.java | Core workflow helpers, idempotency, handler dispatch |
| backend/.../workflow/service/WorkflowApprovalService.java | Approve/reject with @Version and idempotencyKey |
| backend/.../workflow/service/WorkflowSubmitService.java | Submit/resubmit with @Transactional |
| backend/.../workflow/service/WorkflowWithdrawService.java | Withdraw with @Transactional |
| backend/.../workflow/service/WorkflowEngine.java | Facade, delegates to sub-services |
| backend/.../workflow/controller/WorkflowController.java | Controller with PURCHASE_REQUEST in switch |
| backend/.../workflow/entity/WfTask.java | @Version on taskVersion |
| backend/.../workflow/entity/WfInstance.java | No @Version (acceptable as task-level lock suffices) |
| backend/.../workflow/entity/WfIdempotency.java | Idempotency entity |
| backend/.../workflow/handler/WorkflowBusinessHandler.java | Interface with isCritical() |
| backend/.../cost/strategy/ContractCostStrategy.java | Contract cost gen (sets tax fields) |
| backend/.../cost/strategy/MaterialReceiptCostStrategy.java | Material receipt cost gen (no tax fields) |
| backend/.../cost/strategy/SubMeasureCostStrategy.java | Subcontract measure cost gen (no tax fields) |
| backend/.../cost/strategy/VarOrderCostStrategy.java | Variation cost gen (no tax fields) |
| backend/.../cost/strategy/CostSubjectResolver.java | Subject resolution with 3-tier fallback |
| backend/.../cost/service/CostGenerationService.java | Strategy dispatcher |
| backend/.../inventory/service/MatStockService.java | Stock in/out with @Version retry |
| backend/.../inventory/entity/MatStock.java | @Version on version field |
| backend/.../settlement/service/StlSettlementService.java | Settlement CRUD, compute, contractId check, warranty calc |
| backend/.../settlement/entity/StlSettlement.java | Settlement entity |
| backend/.../settlement/handler/SettlementWorkflowHandler.java | Approval locking |
| backend/.../contract/entity/CtContract.java | warrantyRate as BigDecimal |
| backend/.../cost/entity/CostItem.java | Cost item entity with tax fields |
| backend/.../notification/controller/NotificationController.java | SSE /stream endpoint |
| frontend-admin/nginx.conf | proxy_buffering off for SSE |
| db/migration/V4__init_cost_payment_tables.sql | uk_cost_source_item |
| db/migration/V3__init_workflow_tables.sql | uk_wf_idempotency, uk_wf_instance_business |
| db/migration/V12__init_phase2_tables.sql | stl_settlement: MISSING (tenant_id, contract_id) unique |
| db/migration/V35__init_inventory_tables.sql | mat_stock: uk_ms_warehouse_material + version |

## Appendix: isCritical() Handler Summary (All return true)
- ContractWorkflowHandler, MaterialReceiptWorkflowHandler, SubMeasureWorkflowHandler
- VarOrderWorkflowHandler, CtContractChangeWorkflowHandler, PayRequestWorkflowHandler
- PurchaseRequestWorkflowHandler, PurchaseOrderWorkflowHandler, SettlementWorkflowHandler
- CostTargetWorkflowHandler

---

*Report generated by Dimension 5 business logic review. Findings: 4 issues (1 P0, 3 P1, 0 P2/P3 issues), 8 confirmed-correct items.*
