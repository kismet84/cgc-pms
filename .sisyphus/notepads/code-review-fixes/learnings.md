# Learnings - Code Review Fixes

## Conventions
- Backend: `server.servlet.context-path: /api` in `application.yml` — all Controllers must NOT include `/api` in their `@RequestMapping`
- Frontend: axios `baseURL=/api` — all API calls use paths relative to `/api`
- `WorkflowBusinessHandler.isCritical()` controls whether handler exceptions propagate (rollback) or are swallowed
- Cost generation uses strategy pattern with `CostGenerationStrategy` interface and `sourceType` discriminator
- Multi-tenant: All Service methods must use `UserContext.getCurrentTenantId()` for data isolation

## Key Patterns
- `ApplicationListController` does NOT have `/api` in `@RequestMapping` (correct pattern)
- `ContractController` does NOT have `/api` in `@RequestMapping` (correct pattern)
- `PayApplicationController` does NOT have `/api` in `@RequestMapping` (correct pattern)
- Only `CostLedgerController` and `CostSubjectController` have the duplicate `/api` bug

## Cost Summary Contract Alignment (Issue #5)
- Backend `CostSummaryVO` = per-subject row (existing, used by other consumers)
- Backend `CostProjectSummaryVO` = project-level aggregate (new, maps to frontend's `CostSummaryVO`)
- `refreshSummary` changed from `void` → `CostProjectSummaryVO`; internally calls `getProjectSummary` after refresh logic
- `getProjectSummary` aggregates `contractLockedCost`, `actualCost`, `paidAmount` across all subjects via stream reduce
- `dynamicCost = contractLockedCost + actualCost`, `costDeviation = dynamicCost - targetCost`
- `targetCost` at project level comes from `PmProject` entity, NOT sum of subjects (all subjects share same project targetCost)
- `getSummary()` and `getSummaryHistory()` signatures preserved (still return `List<CostSummaryVO>` for history endpoint)

## Frontend Type Verification (2026-06-11)
- `pnpm type-check` passes (exit code 0) — zero type errors across all frontend files
- `CostSummaryVO` (types/cost.ts:84-94): already has `projectId`, `projectName`, `targetCost`, `contractLockedCost`, `actualCost`, `paidAmount`, `dynamicCost`, `costDeviation`, `subjects: CostSubjectSummaryVO[]` — matches backend `CostProjectSummaryVO` structure exactly
- `CostSubjectSummaryVO` (types/cost.ts:72-81): has per-subject fields matching backend `CostSummaryVO`
- `getCostSummary()` (cost.ts:39-44): returns `CostSummaryVO` — correct
- `refreshCostSummary()` (cost.ts:47-52): returns `CostSummaryVO` — correct
- `getBasisList()` (payment.ts:49-54): returns `PayApplicationBasisVO[]` — matches new backend return type
- `summary.vue`: accesses `summary.targetCost`, `summary.subjects`, etc. — all field names correct
- `payment/index.vue:204-205`: `getBasisList(record.id)` → `.map()` on result — works correctly
- No `CostProjectSummaryVO` references leak into frontend codebase (frontend-only naming is self-consistent)
- Decision: No rename needed (`CostSummaryVO` → `CostProjectSummaryVO`) — frontend types are self-consistent and structural match is correct

## MatReceiptService.saveItemsBatch Fix (Issue #2)
- Bug: Old receipt items deleted without subtracting from orderItem.receivedQuantity, then new items ADD quantities → accumulation on repeated saves
- Fix: Before deleting old items, query them and subtract their actualQuantity from the associated MatPurchaseOrderItem.receivedQuantity
- Defensive: set receivedQuantity to ZERO if subtraction would go negative
- Pattern: subtract-old-then-add-new ensures idempotent re-save behavior

## MatPurchaseOrderService Approval Guards (2026-06-11)
- Pattern: Use `!"DRAFT".equals(order.getApprovalStatus())` to block both APPROVING and APPROVED in a single check
- Applied to: `update()`, `delete()`, `saveItemsBatch()` — any mutating operation except `submitForApproval()`
- Error code: `ORDER_IN_APPROVAL` — consistent across all three guards
- submitForApproval() already has its own `!"DRAFT"` check (duplicate submit prevention), left unchanged
- No `costGeneratedFlag` on `MatPurchaseOrder` entity — not needed for guards

## VarOrderService Edit/Delete Guards (Issue #4)
- Guard pattern: after tenant/null check, before business logic, add two checks:
  1. `!"DRAFT".equals(entity.getApprovalStatus())` → throw BusinessException
  2. `entity.getCostGeneratedFlag() != null && entity.getCostGeneratedFlag() == 1` → throw BusinessException
- Applied to: `update()` (var: `existing`), `saveItems()` (var: `order`), `delete()` (var: `existing`)
- Error messages differ slightly per operation: "不可编辑" for update/saveItems, "不可删除" for delete
- `submitForApproval()` already has its own `DRAFT` check, not modified

## Multi-Tenant Isolation Fix: CostSummaryService (Issue #7)
- Pattern: Overload pattern — add `(Long tenantId, Long projectId)` variant containing actual logic; existing `(Long projectId)` delegates with `UserContext.getCurrentTenantId()`
- Applied to: `refreshSummary()` and `updatePaidAmount()`
- `scheduledRefresh()` uses `project.getTenantId()` from the queried entity (NOT UserContext)
- `updatePaidAmount(tenantId, projectId)`: PayRecord query + CostSummary update both filtered by tenantId
- `PayRecordService` caller unchanged — it's on the request thread, so the convenience `updatePaidAmount(projectId)` delegates correctly
- `getSummary()` / `getProjectSummary()` / `getSummaryHistory()` left untouched per task spec — they're request-thread-only

## WorkflowEngineIntegrationTest Repeatability Fix (Issue #8, 2026-06-11)
- Root cause: `uk_wf_instance_business (business_type, business_id)` unique key on wf_instance. Hardcoded `businessId=100L` caused collision on re-run.
- Fix: `RUN_ID = System.currentTimeMillis()` as base, each test uses `RUN_ID + 1` through `RUN_ID + 10`
- Profile: Changed from `@ActiveProfiles("local")` (MySQL cgc_pms) → `@ActiveProfiles("test")` (MySQL cgc_pms_test) for database isolation
- Cleanup: `@AfterAll` with JdbcTemplate deletes from wf_idempotency, wf_record, wf_task, wf_node_instance, wf_instance by businessId range
- `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` allows non-static `@AfterAll` to access injected JdbcTemplate
- H2 NOT used: Flyway migrations use MySQL-specific `JSON_OBJECT(key, value)` syntax (H2 requires colon syntax). Also `COLLATE=utf8mb4_0900_ai_ci`, `ENGINE=InnoDB` — incompatible with H2 even in MODE=MySQL. The README's claim of H2 support for local dev is incorrect (application-local.yml actually uses MySQL).
- Created `backend/src/test/resources/application-test.yml` to disable Redis/MinIO when test profile is active (overrides main application-test.yml which enables both)

## RegressionFixVerificationTest (2026-06-11)
- New test class: `backend/src/test/java/com/cgcpms/RegressionFixVerificationTest.java`
- Follows Phase2FullChainIntegrationTest patterns: `@SpringBootTest`, `@ActiveProfiles("local")`, `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)`, `@Transactional`
- Demo data IDs: PROJECT_ID=10001, CONTRACT_ID=30001, PARTNER_ID=20001
- F10: Verifies MatReceiptService.saveItemsBatch does NOT double-count receivedQuantity on repeated calls (subtract-old-then-add-new pattern)
- F17: Verifies edit/delete guards on MatPurchaseOrder (ORDER_IN_APPROVAL) and MatReceipt (RECEIPT_IN_APPROVAL) block operations when approvalStatus != DRAFT
- Java lambda effectively-final requirement: Variables used in lambda expressions must not be reassigned anywhere; snapshot local variables needed for `assertThrows(() -> ...)` calls
- Entity field names: MatPurchaseOrderItem uses `specification` (not `spec`), `quantity` (not `orderedQuantity`); MatReceiptItem uses `actualQuantity`, `qualifiedQuantity`
- MatReceiptService.create() auto-fills contractId and partnerId from associated purchase order
- Cleanup pattern: reset approvalStatus to DRAFT and costGeneratedFlag to 0 before soft-deleting with mapper.deleteById()
- Idempotent @BeforeEach cleanup: delete leftover test data (remark like "Regression-") to prevent `Duplicate entry` on `uk_mat_po_code` caused by aborted @Transactional rollbacks. Delete child items first, then parent. Must run after UserContext.set() so tenantId=0 queries work.
