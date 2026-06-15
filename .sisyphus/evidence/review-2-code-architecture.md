# Review 2: Backend Code & Architecture Review

**Date**: 2026-06-15
**Scope**: Dimension 2 static analysis - N+1 queries, empty catches, code duplication, @Valid gaps, mass assignment, transaction boundaries, oversized classes
**Coverage**: 51 Service files, 41 Controller files, strategy classes, utility classes
**Max findings**: 15
---

## Summary

| Severity | Count | Key Areas |
|----------|-------|-----------|
| P1 (High) | 4 | N+1 pattern, empty catch, missing readOnly hint, inconsistent VO mapping |
| P2 (Medium) | 5 | Oversized classes, duplicate methods, proxy fragility, result inconsistency, @Valid inconsistency |
| P3 (Low) | 5 | Mass assignment pattern, @SuppressWarnings count, catch naming, self-invocation, builder misuse |
| **Total** | **14** | |
---

## P1 Findings (High - should fix)

### [D2-001] | P1 | PayApplicationService.java:471-483 | N+1 Queries in getById
**File**: backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java

The toVO(PayApplication app) single-arg method performs 3 individual selectById calls for project/contract/partner names:
- pmProjectMapper.selectById(app.getProjectId()) - extra query
- ctContractMapper.selectById(app.getContractId()) - extra query
- mdPartnerMapper.selectById(app.getPartnerId()) - extra query

**Repro**: Every GET /pay-applications/{id} request generates 3 extra DB queries.

**Impact**: The getPage() method (line 97) correctly uses the batch overload with pre-fetched maps. The getById() method (line 139) uses the single-arg overload, causing 3 extra queries per request.

**Fix**: Refactor getById() to use the batch-prefetch pattern, or join the related tables.
---

### [D2-002] | P1 | InvoiceService.java:210 | Empty Catch Block (No Logging)
**File**: backend/src/main/java/com/cgcpms/invoice/service/InvoiceService.java

The catch block for PDF document.close() has zero logging:

```java
catch (Exception ignored) {
    // ignore close errors
}
```

**Impact**: Zero logging makes resource-close failures impossible to diagnose.

**Fix**: Add log.debug("Failed to close PDF document", e) at minimum.
---

### [D2-003] | P1 | Zero Services Use readOnly=true on Query Methods
**Scope**: All 42 service files with @Transactional. Zero query methods use @Transactional(readOnly = true).

**Impact**: Missing readOnly = true prevents documentation of developer intent and potential JDBC/flush optimizations.

**Fix**: Add @Transactional(readOnly = true) to all read-only service methods (e.g., getPage, getById, list).
---

### [D2-004] | P1 | PayApplicationService: Two toVO Overloads - One Causes N+1
**File**: backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java

Single-arg toVO(PayApplication) performs N+1 (3 selects per call). Batch overload with pre-fetched Maps is O(1). Both exist and getById() calls the wrong one.

**Fix**: Deprecate single-arg overload, refactor getById() to use batch version.
---

## P2 Findings (Medium - should address)

### [D2-005] | P2 | 4 Service Classes > 500 Lines

| File | Lines |
|------|-------|
| .../settlement/service/StlSettlementService.java | 590 |
| .../cost/service/CostSummaryService.java | 572 |
| .../alert/service/AlertEvaluationService.java | 567 |
| .../dashboard/service/DashboardService.java | 532 |

**Impact**: 500+ line classes tend to violate Single Responsibility Principle.

**Fix**: Extract cross-cutting concerns into separate utility classes or delegate services.
---

### [D2-006] | P2 | WorkflowCoreService.java:165-196 | Near-Duplicate Cancel Methods
**File**: backend/src/main/java/com/cgcpms/workflow/service/WorkflowCoreService.java

cancelPendingTasksInNode() (line 165) and cancelAllPendingTasks() (line 183) share ~90% identical logic (batch update by filter).

**Fix**: Extract common batch-cancel utility method.
---

### [D2-007] | P2 | Self-Invocation Bypasses @Transactional (2 Locations)
**Files**: AlertEvaluationService.java:107-108, CostSummaryService.java:408

Both services call @Transactional-annotated methods via direct self-invocation, bypassing Spring AOP.
While REQUIRED propagation still works, this is fragile if REQUIRES_NEW is later introduced.

**Fix**: Use AopContext.currentProxy() consistently or annotate only the outer method.
---

### [D2-008] | P2 | VarOrderCostStrategy.java:81 | Inconsistent costSubjectId Resolution
**File**: backend/src/main/java/com/cgcpms/cost/strategy/VarOrderCostStrategy.java

Uses item.getCostSubjectId() directly vs. all other strategies using the resolver. No null-fallback.

**Fix**: Add null-coalescing to use resolver when item-level subject ID is null.
---

### [D2-009] | P2 | ProfileController.java:34 | Inconsistent @Valid Usage
**File**: backend/src/main/java/com/cgcpms/system/controller/ProfileController.java

updateProfile() lacks @Valid on @RequestBody; changePassword() has it. While DTO currently has no constraints, this is a future-proofing gap.

**Fix**: Add @Valid for consistency.
---

## P3 Findings (Low - monitor / good to know)

### [D2-010] | P3 | 30+ Controllers Bind Entity Directly (Mass Assignment)

30+ controllers bind Entity classes directly via @RequestBody. Mitigated by @JsonProperty(READ_ONLY) on 45 fields across 15 entity files. Risk: unprotected fields are writable.

**Recommendation**: Introduce dedicated Create/Update DTOs per module.
---

### [D2-011] | P3 | @SuppressWarnings Audit - 3 Occurrences, All Justified

| File | Line | Justification |
|------|------|---------------|
| UserContext.java | 25 | ThreadLocal Object cast (necessary) |
| PayApplicationService.java | 67 | 12-param constructor (SonarQube S107) |
| MatReceiptService.java | 471 | Reflective getId() via generics |

**Verdict**: All justified. No abuse detected.
---

### [D2-012] | P3 | WorkflowCoreService.java:241 | Misleading ignored Variable Name
**File**: backend/src/main/java/com/cgcpms/workflow/service/WorkflowCoreService.java

Variable named "ignored" but exception IS logged via log.error. Misleading during reviews.

**Fix**: Rename to e or ex.
---

### [D2-013] | P3 | AOP Self-Invocation Workaround (Two Locations)
**Files**: CostSummaryService.java:408, AlertEvaluationService.java:84

Uses AopContext.currentProxy() which requires exposeProxy=true. If config changes, @Transactional silently becomes a no-op.

**Fix**: Extract transactional logic into a separate injectable @Service bean.
---

### [D2-014] | P3 | DashboardService Manual VO Construction
**File**: backend/src/main/java/com/cgcpms/dashboard/service/DashboardService.java:508-531

toProjectSummary() and toContractItem() manually copy fields one-by-one. No compile-time check when entities gain new fields.

**Recommendation**: Use MapStruct or constructor-based builders.
---

## Verified Good Practices (No Finding)

| Check | Status | Detail |
|-------|--------|--------|
| DateTimeFormatter.ofPattern | PASS | Only in DateTimeUtils.java (3 patterns), 28 services reference it |
| CostSubjectResolver usage | PASS | 4 strategy classes use the resolver - duplication eliminated |
| @Valid on Entity @RequestBody | PASS | All 30+ create/update endpoints with Entity binding use @Valid |
| @JsonProperty(READ_ONLY) protection | PASS | 45 fields across 15 entity files protected |
| Empty catch blocks (true empty) | PASS | Only InvoiceService.java:210 (logged as D2-002) |
| @Transactional(rollbackFor) usage | PASS | All strategy classes specify rollbackFor=Exception.class |
---

## Files Referenced

| File | Issues |
|------|--------|
| backend/.../payment/service/PayApplicationService.java | D2-001, D2-004 |
| backend/.../invoice/service/InvoiceService.java | D2-002 |
| backend/.../settlement/service/StlSettlementService.java | D2-005 |
| backend/.../cost/service/CostSummaryService.java | D2-005, D2-013 |
| backend/.../alert/service/AlertEvaluationService.java | D2-005, D2-007 |
| backend/.../dashboard/service/DashboardService.java | D2-005, D2-014 |
| backend/.../workflow/service/WorkflowCoreService.java | D2-006, D2-012 |
| backend/.../cost/strategy/VarOrderCostStrategy.java | D2-008 |
| backend/.../system/controller/ProfileController.java | D2-009 |
| backend/.../cost/strategy/CostSubjectResolver.java | Verified |
| backend/.../common/util/DateTimeUtils.java | Verified |
---

## Next Steps

1. **Immediate**: Fix D2-002 (empty catch) and D2-012 (misleading variable name) - quick wins.
2. **Short-term**: Address D2-001/D2-004 (N+1 in PayApplicationService.toVO), D2-003 (readOnly hint on query methods).
3. **Medium-term**: Refactor top 3 oversized services (D2-005), extract duplicate cancel methods (D2-006), add @Valid to ProfileController (D2-009).
4. **Architectural**: Evaluate DTO-based request binding (D2-010) as part of the next development phase.
