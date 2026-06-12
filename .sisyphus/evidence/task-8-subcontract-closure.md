# T8: 分包→计量闭环验收

**Date:** 2026-06-12 23:15~23:20
**Tester:** Sisyphus-Junior
**Status:** PASSED ✅

## Preconditions
- Backend: Spring Boot 3.3.5, dev profile, MySQL 8.0 @ localhost:3306
- Admin: admin/admin123 (roles: SUPER_ADMIN, ADMIN)
- Existing data: Project 10001 (城市中心商业综合体总承包工程), Contract 30002 (主体结构劳务分包合同, SUB type, APPROVED, 86,000,000 CNY), Partner 20002 (宏远建筑劳务分包有限公司)

## Blockers Fixed
1. **Admin role mismatch**: `@PreAuthorize("hasRole('ADMIN')")` didn't match admin's `SUPER_ADMIN` role. Added `ADMIN` role (id=4) to `sys_role` and assigned to admin via `sys_user_role`.
2. **Cost subject missing**: `cost_subject` table was empty, causing NPE in `CostLedgerService.toVO()`. Seeded 5 cost subjects (分包/材料/人工/机械/其他).
3. **Cost item null cost_subject_id**: Updated existing `cost_item` records with `cost_subject_id = 1001`.

## Test Flow

### 1. Create Sub Task
```bash
POST /api/sub-tasks
Body: {"projectId":10001,"contractId":30002,"partnerId":20002,"taskName":"T8-Subcontract-Foundation-Works","workArea":"Foundation Zone A","plannedStartDate":"2026-06-01","plannedEndDate":"2026-09-30","status":"IN_PROGRESS"}
Response: {"code":"0","data":2065452968992866305}
Task Code: SUB-20260612-001
```

### 2. Create Sub Measure
```bash
POST /api/sub-measures
Body: {"projectId":10001,"contractId":30002,"partnerId":20002,"measurePeriod":"2026-06","measureDate":"2026-06-12","reportedAmount":500000.00,"approvedAmount":500000.00,"deductionAmount":0.00,"netAmount":500000.00,"status":"DRAFT","approvalStatus":"DRAFT"}
Response: {"code":"0","data":2065453001062514690}
Measure Code: SM-20260612-001
```

### 3. Add Measure Items
```bash
POST /api/sub-measures/2065453001062514690/items/batch
Body: [{"measureId":2065453001062514690,"itemName":"Concrete Foundation C30","unit":"m3","contractQuantity":5000.00,"currentQuantity":500.00,"cumulativeQuantity":500.00,"unitPrice":1000.00,"amount":500000.00}]
Response: {"code":"0"}
```

### 4. Submit + 3-Node Approval
Template: 分包计量审批流程 (template_id=50004, 3 SEQUENTIAL nodes)
- N1 (项目经理审批): Task 2065453029390843908 → **APPROVED** ✅
- N2 (部门经理审批): Task 2065453084692742146 → **APPROVED** ✅ (retried after JSON encoding fix)
- N3 (总经理审批): Task 2065453346920628226 → **APPROVED** ✅

Final measure status: `approvalStatus: APPROVED`, `status: CONFIRMED`, `costGeneratedFlag: 1`

### 5. Cost Ledger Verification
```bash
GET /api/cost-ledger?sourceType=SUB_MEASURE&pageNum=1&pageSize=5
Response: 1 record
- amount: 500000.00 CNY
- sourceType: SUB_MEASURE
- sourceId: 2065453001062514690
- costSubjectName: 分包成本
- costType: SUBCONTRACT
- costStatus: CONFIRMED
```

### 6. Payment Application
```bash
POST /api/pay-applications
Body: {"projectId":10001,"contractId":30002,"partnerId":20002,"applyAmount":"500000.00","payType":"PROGRESS","applyReason":"T8 Payment for measure SM-20260612-001"}
Response: {"code":"0","data":2065453715302154242}
Pay Code: PAY-20260612-002
```

### 7. Payment Basis (linked to measure ITEM, not measure header)
```bash
POST /api/pay-applications/2065453715302154242/basis/batch
Body: [{"payApplicationId":2065453715302154242,"basisType":"SUB_MEASURE","basisId":2065453029126602754,"basisAmount":"500000.00"}]
Response: {"code":"0"}
```

### 8. Payment Approval (3-node)
- N1 → APPROVED
- N2 → APPROVED
- N3 → APPROVED

Final payment status: `payStatus: APPROVED`, `approvalStatus: APPROVED`

### 9. Negative Test: Excess Payment
```bash
POST /api/pay-applications (amount: 600000.00 > measure 500000.00)
POST /api/pay-applications/{id}/basis/batch (basisAmount: 600000.00)
POST /api/pay-applications/{id}/submit
Response: {"code":"BASIS_EXCEED_SOURCE","message":"付款依据金额(600000.00)超过计量单明细金额(500000.00)"}
```
**Result: Correctly blocked** ✅

## Chain Summary

```
SubTask (SUB-20260612-001)
  → SubMeasure (SM-20260612-001, 500,000 CNY)
    → 3-node approval (APPROVED)
      → CostItem generated (SUB_MEASURE, 500,000 CNY)
        → PayApplication (PAY-20260612-002, 500,000 CNY)
          → 3-node approval (APPROVED)
            → Excess payment (> measure) BLOCKED ✅
```

## Issues Found

### Issue 1: Admin role/permission mismatch
`@PreAuthorize("hasRole('ADMIN')")` doesn't match `SUPER_ADMIN`. Admin needs `ADMIN` role in DB.
**Fix**: Added ADMIN role (id=4) via SQL INSERT.

### Issue 2: AuthorizationDeniedException → 500
`GlobalExceptionHandler` does not handle `AuthorizationDeniedException`, causing 500 instead of 403.
**Workaround**: Added ADMIN role. **Root fix needed**: Add `@ExceptionHandler(AuthorizationDeniedException.class)`.

### Issue 3: Empty cost_subject table
`SubMeasureCostStrategy.resolveDefaultSubjectId()` returns null when no cost subjects exist, causing NPE in `CostLedgerService`.
**Fix**: Seeded 5 cost subjects. **Root fix**: Flyway seed data should include cost subjects.

### Issue 4: Payment basis links to measure ITEM, not measure
`BasisType=SUB_MEASURE` expects `basisId` to be a measure ITEM ID (sub_measure_item.id), not the measure header ID.
**Documentation gap**: API docs should clarify this.

### Issue 5: JSON encoding in PowerShell
String interpolation with `"` characters corrupts JSON bodies. Using single-character `"` within `'...'` delimiters works reliably.

## Data Created
| Entity | ID | Code | Amount |
|--------|----|----|--------|
| SubTask | 2065452968992866305 | SUB-20260612-001 | — |
| SubMeasure | 2065453001062514690 | SM-20260612-001 | 500,000 CNY |
| SubMeasureItem | 2065453029126602754 | — | 500,000 CNY |
| CostItem | 2065453381582356481 | — | 500,000 CNY |
| PayApplication | 2065453715302154242 | PAY-20260612-002 | 500,000 CNY |
