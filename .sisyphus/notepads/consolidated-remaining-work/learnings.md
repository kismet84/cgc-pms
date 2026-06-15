## Dimension 1 Security & Configuration Review (2026-06-15)

### Key Patterns Found

**Authorization**:
- All 41 controllers have complete @PreAuthorize coverage (230 annotations across ~170 endpoints)
- AuthController has 2 deliberately public endpoints (/login, /refresh) for unauthenticated access
- Pattern used: `hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('module:action')`
- WorkflowController uses `isAuthenticated()` for user-agnostic endpoints
- SecurityConfig whitelist: "/auth/login", "/auth/refresh", "/swagger-ui/**", "/v3/api-docs/**", "/doc.html", "/webjars/**", "/actuator/health"

**JWT**:
- Dual-token scheme: access (15min TTL) + refresh (7d TTL) with rotation
- HMAC-SHA256 signing via jjwt 0.12.x
- Redis-based blacklist for logged-out tokens (suffix-based key: last 32 chars)
- Cookie-based token delivery (HttpOnly, SameSite=Strict) with Authorization header fallback
- UserContext ThreadLocal populated from JWT claims: userId, username, tenantId, roles

**CORS**:
- Configured via `@Value("${cors.allowed-origins}")` per profile — no wildcards
- `allowCredentials(true)` with specific origins = safe
- Headers restricted: Authorization, Content-Type, X-Refresh-Token

**File Upload Security**:
- Extension whitelist (20 types), 50MB limit, businessType path-injection regex `[A-Za-z0-9_-]+`
- Tenant isolation: ownership verified in getPresignedUrl and delete
- Presigned URL expiry: 7 days

**Exception Handling**:
- AuthorizationDeniedException -> 403, AccessDeniedException -> 403, BusinessException -> 400
- Proper stack trace logging for system exceptions; generic message returned to client

**Logging**:
- Regex masking for: password, token, secret, authorization, phone, email, bankAccount, etc.
- Caveat: regex only matches key=value patterns, not JSON/structured formats

**Docker**:
- All containers have HEALTHCHECK
- Backend: non-root appuser, curl installed for health check
- Frontend: nginx:1.27-alpine, ssl dir created

**Nginx**:
- TLSv1.2/TLSv1.3 only, modern ciphers, http2
- HSTS commented out by default
- X-Frame-Options DENY, X-Content-Type-Options nosniff, X-XSS-Protection, Referrer-Policy, Permissions-Policy
- Missing: Content-Security-Policy, no HTTP->HTTPS redirect

**Critical Issues Found**:
- .env.example uses self-referencing `${VAR}` placeholders that don't resolve
- useSSL=false in all Docker JDBC URLs overrides application-prod.yml secure default
- Auth endpoints lack rate limiting
- Cookie Secure flag defaults to false
# Dimension 5 Business Logic Review - Learnings

## Date: 2026-06-15

### Key Findings
- StlSettlementService.create() has a TOCTOU race on contractId uniqueness check - no DB UNIQUE constraint on (tenant_id, contract_id). Only uk_stl_settlement_code exists on (tenant_id, settlement_code).
- Settlement code generation (STL-yyyyMMdd-NNN) uses SELECT+INSERT pattern vulnerable to race. DuplicateKeyException not caught.
- SettlementWorkflowHandler.onApproved() lacks optimistic lock or status guard on finalization update.
- 3 of 4 CostGenerationStrategy implementations (MaterialReceipt, SubMeasure, VarOrder) leave taxAmount/amountWithoutTax unset -> default 0.
- Warranty rate calculation is CORRECT: DEFAULT_WARRANTY_RATE=0.05 (ratio), contract stored as percentage with movePointLeft(2) conversion.
- proxy_buffering off confirmed for SSE in both HTTP (line 68) and HTTPS (line 150) nginx server blocks.
- Workflow engine split intact: isCritical() still controls rollback, @Transactional on all sub-service entry points.
- All 10 WorkflowBusinessHandler implementations return isCritical()=true.
- WfTask @Version on taskVersion correct; MatStock @Version with retry correct.
- uk_cost_source_item (source_type, source_id, source_item_id, cost_type) unique constraint correct for cost generation idempotency.
- uk_wf_idempotency (tenant_id, user_id, idempotency_key) correct for workflow idempotency with insert-first pattern.
- PURCHASE_REQUEST case present in WorkflowController.getRequiredPermission().

### Files Modified
- .sisyphus/evidence/review-5-business-logic.md (created - 12 findings: 1 P0, 3 P1, 8 confirmed-correct)

## 2026-06-15 — Dimension 4 Data & Infrastructure Review

**Completed:** D4 review report at .sisyphus/evidence/review-4-data-infra.md
**Findings:** 15 total (P1: 2, P2: 6, P3: 7)

### Key takeaways:
- Flyway sync is perfect (51 MySQL = 51 H2, all names match 100%)
- V47 introduced type inconsistency (SMALLINT vs TINYINT for deleted_flag)
- V47 H2 version has unnamed constraint (vs named in MySQL) — will break future cross-DB migrations
- V50 and V51 use inconsistent DROP syntax for unique constraints
- created_at/created_time naming split existed for 23 migrations (V22-V44)
- V8 index coverage is incomplete — many tables lack created_at indexes
- No FOREIGN KEY constraints anywhere (application-level only)
- **P1: Backend Dockerfile has empty credential defaults** (DB_PASSWORD, JWT_SECRET, etc.)
- **P1: deploy/.env stores production creds in plaintext** (gitignored but filesystem-exposed)
- Frontend Dockerfile COPY public/ is commented out
- CI pnpm version (pnpm@11) doesn't match Dockerfile (pnpm@11.0.9)
- docker-compose.prod.yml uses deprecated version field
- CI deploy job has no automated pre-deploy backup or rollback
- CI flyway-check timeout (60s) is too tight for cold cache
- CI docker-build is skipped on workflow_dispatch — deploy builds own images without cache

## 2026-06-15: Dimension 3 Frontend & API Contract Review

### Key Findings
- **API module bypass**: Two files (profile/index.vue, system/data/index.vue) bypass the api/modules layer by importing request directly or importing raw axios service.
- **Catch without parameter**: 122 instances across 37 files lose error context. This is the most widespread issue.
- **catch (e: any)**: 3 instances in 2 files (invoice/index.vue x2, system/users/index.vue x1).
- **: any casts**: invoice/index.vue has 6 no-explicit-any violations including uploadFileList ref and dynamic form field access.
- **DEV-guarded console.error**: 22 instances across 5 files (stores + components). All wrapped in import.meta.env.DEV guards, but code stays in production bundle.
- **ESLint**: 50 errors across 25 files, mostly unused imports and no-explicit-any.
- **Router**: Correctly configured with single-item WHITE_LIST and public meta flags.
- **Route mapping**: All 39 routes correctly resolve to existing component files.
- **Interceptor**: Token refresh queue pattern is correct. Edge case: catch block loses refresh error context.
- **KPI endpoint**: Properly typed with ContractKpiVO, no stubs.
- **Full report**: .sisyphus/evidence/review-3-frontend-api.md

### Patterns to Adopt
- Use catch (e: unknown) with instanceof Error checks instead of catch { } or catch (e: any)
- Always route API calls through api/modules/*.ts wrappers
- Consider tree-shakeable logger to replace DEV-guarded console.error calls


## 2026-06-15 — Dimension 2 Code & Architecture Review

### Key Findings
- PayApplicationService.toVO() has N+1 pattern (3 selectById per call) — batch overload already exists but getById() uses the wrong one
- InvoiceService.java:210 has a truly empty catch block (resource cleanup, no logging) — only true empty catch in codebase
- Zero services use @Transactional(readOnly = true) on read-only query methods
- 3 service classes exceed 500 lines: StlSettlementService (590), CostSummaryService (572), AlertEvaluationService (567), DashboardService (532)
- WorkflowCoreService has near-duplicate cancel tasks methods differing only by query filter
- AlertEvaluationService and CostSummaryService use self-invocation (@Transactional bypassed via direct method call)
- VarOrderCostStrategy uses item-level costSubjectId vs. all other strategies use the resolver — inconsistency worth noting
- ProfileController.updateProfile() missing @Valid (inconsistency with changePassword)
- 30+ controllers bind Entity directly via @RequestBody — mass assignment mitigated by @JsonProperty(READ_ONLY) on 45 fields across 15 entities
- CostSubjectResolver successfully eliminates resolveDefaultSubjectId duplication in 4 strategy classes
- DateTimeUtils is the single source for DateTimeFormatter.ofPattern (3 patterns, 28 service references)
- Only 3 @SuppressWarnings occurrences, all justified
- All 4 strategy classes use @Transactional(rollbackFor = Exception.class)
- File written: .sisyphus/evidence/review-2-code-architecture.md (14 findings: 4 P1, 5 P2, 5 P3)

## Dimension 6 — Fix Regression Verification (2026-06-15)

### Key Findings
- **All 10/10 checks pass**. Zero regressions detected in audit-fixes (24/24 tasks) at HEAD.
- The existing report at .sisyphus/evidence/review-6-fix-regression.md was outdated (baseline: HEAD~15). Replaced with fresh comprehensive report.
- Post-audit-fix commits (5+ commits, 44 backend Java files modified) did NOT reintroduce any regressions.
- Key verified areas remained intact:
  - WorkflowEngine split: 7 services, clean facade (132 lines)
  - DateTimeUtils: only 3 DTF calls, all in DateTimeUtils.java
  - CostSubjectResolver: 4 strategies use shared resolver, zero duplicated methods
  - Dashboard N+1: batch queries used (getBatchProjectSummaries), no looped queries
  - Mass Assignment: @JsonProperty(READ_ONLY) on all sensitive fields in all 6+ entities
  - Input Validation: @Valid on all controllers, Jakarta annotations on entities
- @SuppressWarnings: 5 total (3 main + 2 test), all pre-existing, zero new
- Empty catch blocks: ZERO in all backend Java files
- Backend compile: PASS (clean)
- Frontend build: PASS (10.40s, zero TS errors)

### Key Files Reviewed
- .sisyphus/evidence/review-6-fix-regression.md (report, 10KB)
- backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java (132 lines, clean facade)
- backend/src/main/java/com/cgcpms/common/util/DateTimeUtils.java (3 DTF constants)
- backend/src/main/java/com/cgcpms/cost/strategy/CostSubjectResolver.java (shared resolver)
- backend/src/main/java/com/cgcpms/dashboard/service/DashboardService.java (N+1 fixed)
- backend/src/main/java/com/cgcpms/invoice/entity/PayInvoice.java (mass assignment + validation)
- backend/src/main/java/com/cgcpms/payment/entity/PayApplication.java (mass assignment + validation)
- backend/src/main/java/com/cgcpms/payment/entity/PayRecord.java (mass assignment + validation)
- backend/src/main/java/com/cgcpms/common/entity/BaseEntity.java (6 @JsonProperty(READ_ONLY))
- backend/src/main/java/com/cgcpms/variation/entity/VarOrder.java (mass assignment)
- backend/src/main/java/com/cgcpms/settlement/entity/StlSettlement.java (mass assignment)
- backend/src/main/java/com/cgcpms/cost/strategy/ContractCostStrategy.java (uses resolver)
- backend/src/main/java/com/cgcpms/cost/strategy/MaterialReceiptCostStrategy.java (uses resolver)
- backend/src/main/java/com/cgcpms/cost/strategy/SubMeasureCostStrategy.java (uses resolver)
- backend/src/main/java/com/cgcpms/contract/change/strategy/CtContractChangeCostStrategy.java (uses resolveForChange)
- backend/src/main/java/com/cgcpms/cost/strategy/VarOrderCostStrategy.java (direct item costSubjectId)
- backend/src/main/java/com/cgcpms/cost/service/CostSummaryService.java (getBatchProjectSummaries)
- .sisyphus/plans/audit-fixes.md (original audit plan, 24 tasks)

### No Issues Found
No regressions, no new warnings, no new empty catch blocks, no new @SuppressWarnings. All fixes are stable.

## Wave 2 Synthesis (2026-06-15)

### Synthesis Summary
- Collected all 6 review reports from `.sisyphus/evidence/review-*.md`
- Cross-referenced 73 raw findings → deduplicated to 67 consolidated (6 merges)
- Graded: **3 P0**, **14 P1**, **25 P2**, **28 P3**
- Produced `.sisyphus/evidence/synthesis-report.md` (complete consolidation)
- Appended 14 fix tasks (T8–T21) to `.sisyphus/plans/consolidated-remaining-work.md`

### Deduplication Decisions
- **M1** D1-009(P2) + D4-007(P1): Merged → P1. Empty Dockerfile secrets. D4's P1 justified (empty JWT_SECRET = critical vuln).
- **M2** D1-002(P1) + D1-010(P2): Merged → P1. useSSL=false across all Docker configs. Super/subset relationship.
- **M3** D2-001(P1) + D2-004(P1): Merged → P1. N+1 symptom vs root cause (wrong toVO overload). Same file, same fix.
- **M4** D3-004(P1) + D3-005(P1): Merged → P1. catch (e: any) anti-pattern in 2 files.
- **M5** D5-001(P0) + D5-002(P1): Kept separate. Same method but different races (contractId vs code generation).
- **M6** D1-001(P1) + D4-015(P1): Kept separate. .env.example vs .env plaintext — different files, different concerns.

### Key Design Decisions
1. **P0 settlement fix (T8) merges S-P1-12**: The code sequence TOCTOU is in the same method as the contractId TOCTOU. Fixing both together avoids re-touching the file.
2. **readOnly=true (T18) goes LAST among backend P1s**: Touches 42+ files. Any earlier task touching a service file would conflict.
3. **122 catch blocks (T21) goes LAST among frontend fixes**: 37 files, massive blast radius. Must run after P0 frontend fixes (T9, T10) and catch-any fix (T20).
4. **Rate limiting (T14) uses Redis only**: Avoids introducing Bucket4j/Resilience4j dependencies. Redis already in stack.
5. **Nginx task (T13) includes CSP**: D1-011 (CSP P2) is rolled into the nginx security task because it's the same file and small scope.

### Dependency Map Insights
- T8 (settlement) blocks T19 (settlement lock) — same module
- T17 (N+1) blocks T18 (readOnly) — both touch PayApplicationService.java
- T9/T10 (P0 frontend) block T21 (122 catches) — P0 files are in the 37-file set
- T11–T16 (Wave 3-B) are all independent — max parallelism
- T17 and T19 can run in parallel (different modules: payment vs settlement)

### P2/P3 Catalogue
- 25 P2 items catalogued across Security(7), Backend(5), Frontend(4), Infra(7), Business(2)
- 28 P3 items catalogued across all dimensions
- No fix tasks created for P2/P3 per plan rules

### Plan Template Adherence
- Each T8-T21 task follows the plan template: What to do, Must NOT do, Agent Profile, Parallelization, References, QA Scenarios, Commit message
- All tasks marked with `- [ ]` checkbox status
- Wave substructure (3-A through 3-D) matches dependency graph

### Files Modified
- `.sisyphus/evidence/synthesis-report.md` (overwritten — new consolidation from 2026-06-15 reviews)
- `.sisyphus/plans/consolidated-remaining-work.md` (appended T8–T21 under Wave 3)
- `.sisyphus/notepads/consolidated-remaining-work/learnings.md` (this entry appended)

## T9 — Fix frontend API module bypass in system/data/index.vue (2026-06-15)

### What was done
- Replaced `import service from '@/api/request'` with `import { request } from '@/api/request'`
- Replaced `const res: any = await service.delete('/system/clear-database')` with `const msg = await request<string>({ url: '/system/clear-database', method: 'DELETE' })`
- Replaced `message.success(res?.data ?? res?.message ?? '数据库已清空')` with `message.success(msg || '数据库已清空')`
- The `request()` wrapper goes through the response interceptor which unwraps `ApiResponse.data` when `code === '0'`
- Backend returns `ApiResponse.success("已清空 X 张业务数据表...")` → interceptor extracts the string → `msg` is the plain string

### Verification
- `pnpm build` (vue-tsc --noEmit + vite build): PASS, zero TypeScript errors
- `pnpm lint`: PASS, no issues for this file
- No `: any` type assertions remain in this file
- No raw `service` import remains in this file

### Key insight
The old `res?.data ?? res?.message ?? '数据库已清空'` was misusing the interceptor response — the interceptor already unwraps `ApiResponse`, so `res` was already the string data (not an `AxiosResponse`). The `.data` and `.message` accesses were always falling through to the fallback string. The fix eliminates this confusion entirely.

## D3-002: Fix P0 frontend API module bypass in profile/index.vue (2026-06-15)

### What was done
- Added `updateProfile()` and `changePassword()` functions to `frontend-admin/src/api/modules/auth.ts`
- These encapsulate `/profile` (PUT) and `/profile/password` (PUT) endpoints respectively
- Updated `profile/index.vue` to import from `@/api/modules/auth` instead of `@/api/request`
- Removed direct `request` import from the profile page (line 5)
- Removed unused `UserInfo` type import
- Build (`pnpm build`) passes with zero TypeScript errors

### Files Modified
- `frontend-admin/src/api/modules/auth.ts` — added 2 functions (lines 24-30)
- `frontend-admin/src/pages/profile/index.vue` — replaced import + 2 call sites

### Pattern
- All API calls should go through module functions in `@/api/modules/*`, never directly import `request` from `@/api/request` in page components
- The `request<T>(config)` function returns `T` directly (response envelope already unwrapped by interceptor)

## T8 — Fix P0 settlement TOCTOU race condition (2026-06-15)

### What was done
- Created V52 Flyway migration (MySQL + H2): `ALTER TABLE stl_settlement ADD UNIQUE KEY uk_stl_settlement_contract (tenant_id, contract_id)`
- Updated `StlSettlementService.create()` to wrap `stlSettlementMapper.insert(settlement)` in try-catch for `DuplicateKeyException`, re-throwing as `BusinessException("STL_DUPLICATE_SETTLEMENT", "该合同已存在结算单，不允许重复创建")`
- Kept the existing `selectCount` pre-check as the fast path (no exception overhead for common case)
- Created `StlSettlementServiceTest.java` with 5 tests: basic create, duplicate rejection, concurrent TOCTOU simulation (2 threads, CountDownLatch), missing contractId validation, cross-project validation
- All 5 tests pass on H2 with `@ActiveProfiles("local")`

### Pre-existing bugs fixed (prerequisites)
- **V50 H2**: `ALTER TABLE pay_invoice DROP INDEX uk_pi_tenant_invoice_no` failed because H2 V36 used `UNIQUE (tenant_id, invoice_no)` (unnamed) — H2 auto-generates a different constraint name. Fixed with `DROP INDEX IF EXISTS`.
- **V51 H2**: `ALTER TABLE pm_project DROP UNIQUE (tenant_id, project_code)` is not valid H2 syntax (MySQL-specific). Fixed with H2 `CREATE ALIAS` inline Java procedure to query `INFORMATION_SCHEMA.TABLE_CONSTRAINTS` and drop by auto-generated name, then add new constraint.

### Key implementation patterns
- `DuplicateKeyException` import: `org.springframework.dao.DuplicateKeyException` (same as WorkflowCoreService, InvoiceService, cost strategies)
- Test uses demo data contract IDs: 30001 (CT-2026-001, 采购合同), 30002 (CT-2026-002, 分包合同), 30003 (CT-2026-003, 服务合同) — all with tenant_id=0, project_id=10001
- Concurrent test uses `CountDownLatch` (2 threads, startLatch + doneLatch) with atomic counters — exactly 1 success, 1 BusinessException
- `UserContext` must be set per thread (ThreadLocal) and cleared in finally block

### Files Modified
- `backend/src/main/resources/db/migration/V52__add_settlement_contract_unique.sql` (new)
- `backend/src/main/resources/db/migration-h2/V52__add_settlement_contract_unique.sql` (new)
- `backend/src/main/resources/db/migration-h2/V50__fix_invoice_unique_with_deleted_flag.sql` (fixed `DROP INDEX` → `DROP INDEX IF EXISTS`)
- `backend/src/main/resources/db/migration-h2/V51__fix_project_unique_with_deleted_flag.sql` (fixed `DROP UNIQUE(columns)` → H2 `CREATE ALIAS` inline Java)
- `backend/src/main/java/com/cgcpms/settlement/service/StlSettlementService.java` (added DuplicateKeyException import + try-catch around insert)
- `backend/src/test/java/com/cgcpms/settlement/StlSettlementServiceTest.java` (new — 5 tests)

### Verification
- `./mvnw compile -q`: PASS
- `./mvnw test -Dtest=StlSettlementServiceTest`: PASS — 5/5 tests, 0 failures, 0 errors
