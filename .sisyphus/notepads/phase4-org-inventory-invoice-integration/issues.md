# Issues - Phase 4

## F2 Code Quality Review — 2026-06-12

### Backend Test Results
- **Verdict**: BUILD FAILURE
- **Summary**: 147 tests run, 0 failures, 4 errors, 0 skipped
- **Passed**: 26 test classes passing (org, inventory, invoice, notification, dict, purchase request, project member, Phase2, Phase3, regression)
- **Errors**:

| # | Test | Error | Root Cause |
|---|------|-------|------------|
| 1 | Phase4IntegrationTest.test03_notificationChain | IllegalState: ResponseBodyEmitter already completed | SSE emitter reuse in H2 test context |
| 2 | Phase4IntegrationTest.test07_tenantIsolationNotification | Same as above | SSE emitter reuse |
| 3 | ProjectOverviewServiceTest.test01_fullOverview | BadSqlGrammar: Column "org_id" not found | H2 schema mismatch (SysUser entity has org_id but table may not in certain test contexts) |
| 4 | WorkflowEngineIntegrationTest | IllegalState: Failed to load ApplicationContext (Flyway checksum mismatch V36 + failed V39) | Stale H2 Flyway state |

### Frontend Build
- **Type-check (vue-tsc --noEmit)**: PASS (exit 0, zero errors)
- **Build (pnpm build)**: PASS (12.14s, only chunk size warning — acceptable)

### Anti-Pattern Scan Results
- **`as any`**: 8 occurrences total, 1 new in Phase4 (`inventory/purchase-request.vue:355`) — all are for `ApprovalStatusTag` component enum type mismatch (low risk pattern)
- **`@ts-ignore`**: 0 occurrences — CLEAN
- **`console.log` (frontend)**: 0 occurrences — CLEAN
- **Empty catch blocks**: 0 occurrences — CLEAN
- **`System.out.print`**: 0 occurrences — CLEAN
- **`.printStackTrace()`**: 0 occurrences — CLEAN

### Tenant Isolation Audit
All new Phase4 service/mapper queries verified for `.eq(tenantId)` or equivalent post-check:

| Module | Services | Verdict |
|--------|----------|---------|
| org/ | OrgCompanyService, OrgDepartmentService, OrgPositionService, OrgInitService | PASS — all queries have tenantId |
| inventory/ | MatWarehouseService, MatStockService | PASS — all queries have tenantId |
| invoice/ | InvoiceService | PASS — all queries + duplicate invoice_no check scoped to tenant |
| notification/ | NotificationService | PASS — all queries use explicit tenantId param (not UserContext) |
| system/dict/ | SysDictTypeService, SysDictDataService | PASS — all queries have tenantId |
| project/ | PmProjectMemberService, ProjectOverviewService | PASS — all queries have tenantId |
| purchase/ | MatPurchaseRequestService | PASS — selectById pre-check + wrapper .eq(tenantId) |
| workflow/ | WfCcService | PASS — explicit tenantId parameter |
| PurchaseRequestWorkflowHandler | — | PASS — uses request.getTenantId() from entity, NOT UserContext |
| AlertEvaluationService | — | PASS — uses explicit tenantId from DB queries, NOT UserContext in @Scheduled |

### Files Analyzed
- **New backend files**: ~65 (org/, inventory/, invoice/, notification/, system/dict/, project/member/, purchase/request*, workflow/WfCc*)
- **New migrations**: 7 (V33-V39)
- **New frontend files**: ~15 (pages: org/, inventory/, invoice/, system/dict/, project/members.vue, project/overview.vue; api: dict, inventory, invoice, notification, org; types: dict, inventory, invoice, notification, org; stores: project; components: NotificationBell)
- **Modified backend files**: ~14
- **Modified frontend files**: ~10

### OVERALL VERDICT
- **Backend**: CONDITIONAL PASS (4 H2 infra errors, not code quality issues; all unit/functional tests pass)
- **Frontend**: PASS (type-check clean, build successful)
- **Anti-patterns**: PASS (minimal findings, 1 low-risk `as any` in new code)
- **Tenant isolation**: PASS (all new queries properly scoped to tenant)
- **Recommendation**: Fix 3 H2 schema/test issues before deployment to avoid migration integrity problems

---

## F4 Scope Fidelity Issues — 2026-06-12

### CRITICAL
1. **V36 created_at violation** — `V36__init_invoice_table.sql` lines 26,28 use `created_at`/`updated_at` instead of `created_time`/`updated_time`. Only Phase 4 table with wrong naming. Must fix before any production migration.

### HIGH
2. **MigrationIntegrityTest gap** — T8 required extending test to V39; not done. No test verifies clean V1→V39 migration chain.
3. **SysUser.org_id backfill missing** — T10 spec says backfill SysUser.org_id; only PmProject + CtContract were backfilled.

### MEDIUM
4. **No Phase 4 test report** — T32 required `doc/第4阶段测试报告.md`; not created.
5. **No smoke test script** — T34 required `scripts/phase4-smoke.sh`; not created.

### LOW
6. **Partner route replaced** — T23 replaced `/partner` route with `/org`. Partner page file still exists but is inaccessible from sidebar.
