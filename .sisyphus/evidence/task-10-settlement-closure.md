# T10: 结算→归档闭环验收 — Evidence

**Date**: 2026-06-12  
**Performer**: Sisyphus-Junior (development agent)  
**Backend**: localhost:8080 (MySQL 8.0)  
**Auth**: admin / admin123 (SUPER_ADMIN, ADMIN)

---

## 1. Contract Used

**Contract**: CT-2026-002 (id=30002) — 主体结构劳务分包合同
- contractAmount: 86,000,000.00 CNY
- currentAmount: 86,000,000.00 CNY
- contractType: SUB
- contractStatus: PERFORMING
- approvalStatus: APPROVED
- warrantyRate: 5.00% (stored as 5.00 in DB — causes warranty calculation bug, see §8)
- From T8: has SubMeasure SM-20260612-001 (500,000 CNY) + payment (500,000 CNY)

---

## 2. Compute Preview (before create)

```
GET /api/settlements/compute/30002

{
  "contractAmount": "86000000.00",      // contract.currentAmount
  "changeAmount": "0",                  // No confirmed COST varOrders
  "measuredAmount": "500000.00",        // SubMeasure SM-20260612-001
  "paidAmount": "500000.00",            // PayRecord from T8 payment
  "deductionAmount": "0",
  "finalAmount": "86500000.00",         // = 86M + 0 + 500K - 0
  "warrantyAmount": "432500000.00",     // BUG: 86.5M * 5.00 ≠ 5%, see §8
  "unpaidAmount": "-346500000.00"       // Derived from wrong warranty
}
```

---

## 3. Create Settlement

```bash
POST /api/settlements
Body: {"projectId":10001, "contractId":30002, "partnerId":20002, "settlementType":"SUBCONTRACT_SETTLEMENT"}
```

**Result**: ✅ Created
- Settlement ID: `2065456128994082817`
- Settlement Code: `STL-20260612-001` (auto-generated)
- approvalStatus: `DRAFT`
- settlementStatus: `DRAFT`

---

## 4. Auto-Summary Verification

```
GET /api/settlements/2065456128994082817

{
  "settlementCode": "STL-20260612-001",
  "contractAmount": "86000000.00",      // ✅ Snapshot from contract.currentAmount
  "changeAmount": "0.00",               // ✅ SUM(VarOrder COST direction, ownerConfirmFlag=1)
  "measuredAmount": "500000.00",        // ✅ SUM(SubMeasure approvalStatus=APPROVED)
  "paidAmount": "500000.00",            // ✅ SUM(PayRecord payStatus=SUCCESS)
  "deductionAmount": "0.00",           // ✅ User-managed, default 0
  "finalAmount": "86500000.00",         // ✅ contractAmount + changeAmount + measuredAmount - deductionAmount
  "warrantyAmount": "432500000.00",     // ⚠️ BUG (see §8)
  "unpaidAmount": "-346500000.00"       // ⚠️ Derived from buggy warranty
}
```

**Auto-summary verdict**: ✅ Works correctly for contractAmount, changeAmount, measuredAmount, paidAmount, finalAmount. The warranty calculation has a data-model bug (warrantyRate stored as percentage value 5.00 instead of ratio 0.05).

---

## 5. Submit + 3-Node Approval

### Submit
```bash
POST /api/workflow/submit
Body: {"businessType":"SETTLEMENT", "businessId":2065456128994082817, "title":"T10-Settlement for subcontract contract 30002"}
Result: workflow instance 2065456272233758722
```

The `beforeSubmit()` pre-validation passed:
- ✅ No pending (unapproved) VarOrder with COST direction
- ✅ No pending (unapproved) SubMeasure

### Node 1 (Project Manager)
```
POST /api/workflow/tasks/2065456272233758726/approve
Body: {"action":"APPROVE", "idempotencyKey":"t10-stl-node1-*", "comment":"T10 - approve node 1"}
Result: code=0 ✅
```

### Node 2 (Department Manager)
```
POST /api/workflow/tasks/2065456365544439809/approve
Body: {"action":"APPROVE", "idempotencyKey":"t10-stl-node2-*", "comment":"T10 - approve node 2"}
Result: code=0 ✅
```

### Node 3 (General Manager — Final)
```
POST /api/workflow/tasks/2065456390408273922/approve
Body: {"action":"APPROVE", "idempotencyKey":"t10-stl-node3-*", "comment":"T10 - approve node 3 (final)"}
Result: code=0 ✅
```

### SettlementWorkflowHandler.onApproved() executed:
- ✅ approvalStatus → `APPROVED`
- ✅ settlementStatus → `FINALIZED`
- ✅ finalizedAt → `2026-06-12 23:29:18`
- ✅ contract.settlementAmount ← settlement.finalAmount (86,500,000.00) — written to DB

---

## 6. Lock Guard Verification

### Update after approval → BLOCKED ✅
```bash
PUT /api/settlements/2065456128994082817
Body: {"projectId":10001, "contractId":30002, "settlementType":"SUBCONTRACT_SETTLEMENT", "remark":"T10 lock test"}

Result:
{
  "code": "STL_SETTLEMENT_IN_APPROVAL",
  "message": "结算单审批中或已审批，不可编辑"
}
```

### Delete after approval → BLOCKED ✅
```bash
DELETE /api/settlements/2065456128994082817

Result:
{
  "code": "STL_SETTLEMENT_IN_APPROVAL",
  "message": "结算单审批中或已审批，不可删除"
}
```

**Lock guard verdict**: ✅ Works correctly. Both update and delete are blocked for APPROVED settlements (checks `approvalStatus != "DRAFT"` in StlSettlementService).

---

## 7. Contract settledAmount Write-Back

### CtContractVO Gap
The `settlementAmount` field exists in `CtContract` entity and is written back by `SettlementWorkflowHandler.onApproved()`, but it is **NOT exposed** in `CtContractVO`. The contract GET API response does not include `settlementAmount`.

Evidence that write-back executed:
1. ✅ `onApproved()` callback executed (settlement is FINALIZED with finalizedAt timestamp)
2. ✅ Code path in `SettlementWorkflowHandler.onApproved()` includes:
   ```java
   ctContractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
       .eq(CtContract::getId, settlement.getContractId())
       .set(CtContract::getSettlementAmount, settlement.getFinalAmount()));
   ```
3. ⚠️ Cannot verify via REST API (field not in VO). Would need direct DB query to confirm.

**Verdict**: Logic is correct per code review. API gap exists (settlementAmount not in VO). Recommend adding to CtContractVO.

---

## 8. Duplicate Guard → ❌ MISSING

```bash
POST /api/settlements
Body: {"projectId":10001, "contractId":30002, "partnerId":20002, "settlementType":"SUBCONTRACT_SETTLEMENT"}

Result: code=0, settlement created with id=2065456626102992897 (STL-20260612-002)
```

**Duplicate guard verdict**: ❌ **NOT IMPLEMENTED**. A second settlement was successfully created for the same contract (30002) even though the first settlement (STL-20260612-001) was already APPROVED/FINALIZED.

The `StlSettlementService.create()` method does not check:
- Whether another settlement already exists for the same contractId
- Whether contract.settlementAmount is already populated
- Whether contract.contractStatus is SETTLED

This is a **confirmed gap** — the plan expected `error "合同已结算"` but no such guard exists.

The duplicate was cleaned up (deleted) after verification.

---

## 9. Summary — Acceptance Criteria

| # | Criterion | Status | Details |
|---|-----------|--------|---------|
| 1 | 创建结算单(基于已履约合同) | ✅ PASS | STL-20260612-001 created for contract 30002 |
| 2 | 结算单自动汇总合同金额+变更+付款+成本 | ✅ PASS | contractAmount + measuredAmount + paidAmount auto-computed |
| 3 | 结算单可反查来源数据 | ⚠️ PARTIAL | Auto-summary computes from source tables (VarOrder, SubMeasure, PayRecord) but no REST endpoints to list sources |
| 4 | 审批通过后结算单锁定(不可修改) | ✅ PASS | Update & Delete blocked with STL_SETTLEMENT_IN_APPROVAL |
| 5 | 合同settledAmount回写=settlement.finalAmount | ⚠️ PARTIAL | Write-back logic correct but not exposed in CtContractVO |
| 6 | Duplicate guard | ❌ FAIL | No duplicate check; second settlement created successfully |

---

## 10. Issues Found

### Issue 1: Duplicate Settlement Guard Missing (P0)
- **Severity**: P0 (business rule violation)
- **Location**: `StlSettlementService.create()`
- **Fix**: Add check in `create()` for existing non-cancelled settlements with same `contractId`

### Issue 2: settlementAmount Not Exposed in CtContractVO (P2)
- **Severity**: P2 (API completeness)
- **Location**: `CtContractVO` missing `settlementAmount` field
- **Fix**: Add `private String settlementAmount;` to CtContractVO and populate in service layer

### Issue 3: Warranty Rate Data Model Bug (P1)
- **Severity**: P1 (incorrect calculation)
- **Root cause**: `contract.warrantyRate` stored as 5.00 (percentage) but multiplied directly without dividing by 100. Default `DEFAULT_WARRANTY_RATE = 0.05` is correct.
- **Fix**: Either store warrantyRate as ratio (0.05) or divide by 100 in calculation

### Issue 4: Settlement Source Traceability (P2)
- **Severity**: P2 (UX completeness)
- **Note**: No dedicated REST endpoints to list varOrders/payments/subMeasures for a settlement. Source data IS used for auto-computation but not exposed as traceable lists.
