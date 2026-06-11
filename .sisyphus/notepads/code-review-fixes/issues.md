# Issues - Code Review Fixes

## Verified Issues
1. P1: CostLedgerController and CostSubjectController have `/api` prefix in `@RequestMapping` → double `/api/api/`
2. ✅ P1: MatReceiptService.saveItemsBatch accumulates order receivedQuantity without subtracting old values — FIXED: subtract old quantities before deletion
3. P1: PayRequestWorkflowHandler.isCritical() returns false → approval state split risk
4. P1: Approved/cost-generated documents lack edit/delete guards
5. P1: CostSummaryController returns List but frontend expects single object; refresh returns Void
6. P2: PayApplicationController /{id}/basis returns PayApplicationVO instead of basis list
7. P2: CostSummaryService.scheduledRefresh has no tenant context
8. ✅ P2: WorkflowEngineIntegrationTest not repeatable — FIXED: dynamic businessIds (RUN_ID + System.currentTimeMillis()), test profile isolation (cgc_pms_test), @AfterAll cleanup via JdbcTemplate. H2 not used due to MySQL-specific migration syntax (JSON_OBJECT, COLLATE).
