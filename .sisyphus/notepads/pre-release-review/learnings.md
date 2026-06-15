# Pre-Release Review - Learnings

## Task 0: Pre-Flight Health Check (2026-06-13)

### Key Findings
- **mvnw test requires quoted profile flag**: On Windows PowerShell, `-Dspring.profiles.active=local` must be wrapped in quotes: `"-Dspring.profiles.active=local"`, otherwise Maven misinterprets the dots as lifecycle phases.
- **Backend tests: 206 run, 0 failures, 0 errors**. BUILD SUCCESS with H2 local profile. Matches README claim (206 tests, 100% pass).
- **Frontend build: PASS**. Exit code 0, no TypeScript errors. 4457 modules transformed in 14.13s.
- **ESLint baseline shifted**: Previously 34 errors / 668 warnings. Now 39 errors / 0 warnings. Warnings eliminated (good), but 5 new errors introduced (mostly `@typescript-eslint/no-unused-vars` and `@typescript-eslint/no-explicit-any`).
- **Flyway migrations: SYNC**. Both MySQL and H2 have 46 migration files.
- **deploy/.env: P0 BLOCK**. 4 hardcoded passwords (root123, cgc123, redis123, admin123). Must be replaced with `${VAR}` references before Wave 1.
- **.gitignore: SECURE**. `deploy/.env` is covered by `.env` pattern in root `.gitignore`.

### Patterns / Conventions
- Spring Boot test profile: use `-Dspring.profiles.active=local` (not `-Dspring-boot.run.profiles=local` which is for `spring-boot:run` goal)
- Evidence files go in `.sisyphus/evidence/`
- Notepad files: APPEND only, never overwrite

### Issues / Gotchas
- PowerShell argument quoting for Maven: dots in `-D` props need quotes
- `grep` with dots in pattern can trigger confusing errors on Windows

## Wave 1 Launch (2026-06-13 11:31)

### Pre-Flight Summary for Review Agents
- Backend baseline: 206/206 tests pass ✅
- Frontend baseline: 0 TS errors ✅
- ESLint baseline: 39 errors, 0 warnings
- Flyway: 46/46 sync ✅
- Known P0: deploy/.env 4 hardcoded passwords
- Known P0: nginx SSE buffering may need `off`
- Known P1: AuthorizationDeniedException→500 (not 403)
- Known P1: warrantyRate calculation error (×5.00 vs /100)
- Known P0: StlSettlementService missing contractId uniqueness guard
- Known P1: WorkflowController missing PURCHASE_REQUEST case
- Known: Mobile/ is README-only placeholder, scripts/ is start-dev.bat only
- Audit-fixes plan: 24/24 tasks completed, 298 files changed — unverified

## Task 2: Backend Code & Architecture Review (2026-06-13)

### Key Findings
- **P0 Transaction boundary bug**: CostSummaryService.scheduledRefresh() and AlertEvaluationService.scheduledEvaluate() are @Scheduled methods without @Transactional that call @Transactional methods (refreshSummary/evaluateProject) via self-invocation. Spring AOP proxy bypass means @Transactional has NO effect on the scheduled code paths. Physical DELETE commits immediately; subsequent inserts have no rollback safety.
- **N+1 in AlertEvaluationService**: 4 rule methods call ctContractMapper.selectById() inside loops — evaluateMaterialExceedsBudget:209, evaluateSubcontractExceedsContract:246, evaluatePaymentExceedsRatio:310, evaluateWarrantyEarlyRelease:344. Should batch-load with selectBatchIds.
- **N+1 in WorkflowCoreService**: cancelPendingTasksInNode (171-175), cancelAllPendingTasks (183-187), resetActiveNodes (195-199) — individual updateById in loops. Use batch update instead.
- **N+1 in CostSummaryService**: refreshSummary line 150-152 inserts CostSummary rows one-by-one in a loop. Comment says "Batch insert" but it's not.
- **Mass Assignment gaps**: Only PayInvoice and PayApplication have @JsonProperty(READ_ONLY) on tenantId. Most entities (CtContract, PmProject, MatReceipt, SubMeasure, SysUser, etc.) lack this protection on both id and tenantId. Service code mitigates via explicit override, but it's a defense-in-depth gap.
- **@SuppressWarnings**: 3 remaining in main source: UserContext:25 (unchecked, eliminable), MatReceiptService:471 (unchecked, eliminable via interface), PayApplicationService:67 (java:S107, 12-param constructor)
- **Oversized classes (>500 lines)**: CostSummaryService (570), AlertEvaluationService (547), StlSettlementService (541), DashboardService (532), PayApplicationService (530). MatReceiptService at 487 is close.
- **"ignored" catch variable names**: 3 instances (WorkflowCoreService:230, NotificationService:163, MatReceiptService:481) — variable named "ignored" but exception is actually logged. Misleading naming pattern.

### Positive Confirmations
- WorkflowEngine split verified: 7 focused services, clean boundaries, no bugs found
- DateTimeUtils extraction verified: all DateTimeFormatter.ofPattern() centralized in one file, zero duplication
- CostSubjectResolver deduplication verified: 3 strategies use shared resolver; VarOrderCostStrategy correctly uses item.getCostSubjectId()
- Input validation gaps FIXED: PayApplicationController and InvoiceController now have @Valid (previously known gaps resolved)
- All 38 Controllers have @Valid on entity @RequestBody parameters
- BaseEntity protects 6 fields via @JsonProperty(READ_ONLY): createdBy, createdAt, updatedBy, updatedAt, deletedFlag, remark

### Patterns / Conventions
- Spring AOP self-invocation trap: @Transactional on method called from same class via this. has NO effect. Extract to delegate bean or use AopContext.currentProxy().
- @Scheduled methods should carry their own @Transactional (not delegate to internal methods)
- MyBatis-Plus supports selectBatchIds and updateBatchById for bulk operations — use instead of loop+individual calls
- @JsonProperty(READ_ONLY) is the primary defense against JSON mass assignment in Spring Boot (Jackson binding)
- grep regex limitation on Windows: look-ahead/look-behind patterns not supported without --pcre2 flag

### Issues / Gotchas
- grep parsing issues on Windows with special characters like `*`, `(`, `)` — need to use simpler regex or escape properly
- PowerShell ForEach-Object with Get-Content works for line counting but is slow; bash `wc -l` would be faster on Linux
- Self-invocation bypass of AOP proxies is a well-known Spring pitfall that still catches developers

## Task 5: Business Logic Correctness Review (2026-06-13)

### Key Findings (11 issues, 0 P0, 2 P1, 6 P2, 3 P3)

**P1 — Payment amount basis:**
- PayApplicationService.validatePaymentAmount() uses contract.getContractAmount() (original) instead of contract.getCurrentAmount() (post-change) at lines 368 & 385. StlSettlementService correctly uses currentAmount. This means change orders that increase contract value are NOT reflected in payment availability checks.

**P2 — Settlement TOCTOU:**
- StlSettlementService.create() has contractId uniqueness guard (P0-01 FIXED), but uses SELECT-then-INSERT without DB-level UNIQUE constraint. Two concurrent creates can both pass the guard.

**P2 — writeRecord silent fail:**
- WorkflowCoreService.writeRecord() catches Exception on WfInstance lookup but continues to save record with null/default context. Audit trail records may have tenant_id=0.

**P2 — No duplicate workflow submit prevention:**
- WorkflowSubmitService.submit() doesn't check for existing active instances for (businessType, businessId). Same entity can be submitted multiple times.

**P2 — Settlement update contract-change guard missing:**
- update() validates new contract exists but doesn't re-check uniqueness. DRAFT settlement can be moved to a contract that already has a settlement.

**P2 — Warranty rate unit ambiguity:**
- CtContract.warrantyRate has no documentation specifying percentage vs ratio storage. StlSettlementService applies movePointLeft(2) assuming percentage. If frontend sends ratio, off by 100x.

**P2 — VarOrderCostStrategy bypasses CostSubjectResolver:**
- Disagrees with Task 2 finding (which said this is correct). VarOrderCostStrategy uses item.getCostSubjectId() directly, while the other 3 strategies use costSubjectResolver.resolveDefaultSubjectId() with 3-tier fallback. If VarOrderItem has null costSubjectId, generated CostItem gets null — no fallback.

**P3 — Dead code in dispatchToHandler:**
- onRunning() branch is unreachable — notifyHandler only called after instance already set to INSTANCE_APPROVED.

**P3 — Strategy structural duplication:**
- 4 CostGenerationStrategy implementations share ~60% identical template (~150 lines duplicated).

**P3 — Useless try-catch:**
- NotificationController.unreadCount() has try-catch that only logs and re-throws.

### Verified Fixes
- P0-01 (settlement contractId guard): FIXED at StlSettlementService:117-124
- P1-01 (warranty x5.00): FIXED — both computeSettlementAmount and autoFillAmounts now use movePointLeft(2)
- P1-04 (PURCHASE_REQUEST switch): FIXED — WorkflowController:78 includes the case
- P0 (nginx SSE buffering): FIXED — nginx.conf:112 has proxy_buffering off
- Workflow taskVersion optimistic lock: CONFIRMED working (WfTask @Version + updateById checked for 0 rows)
- Inventory @Version: CONFIRMED working (MatStock @Version + retry loop in both stockIn/stockOut)
- SseEmitter implementation: CONFIRMED correct (ConcurrentHashMap + cleanup callbacks)

### Cross-dimension Discrepancy
- Task 2 finding claimed "VarOrderCostStrategy correctly uses item.getCostSubjectId()". I disagree — the other 3 strategies all use CostSubjectResolver with fallback logic. VarOrderCostStrategy is the odd one out. This needs resolution in synthesis.

## Task 3: Frontend & API Contract Review (2026-06-13)

### Key Findings (15 issues, 2 P0, 5 P1, 7 P2, 1 P3)

**P0 — Route mismatch:**
- ContractApproval route at router/index.ts:38 maps to `@/pages/dashboard/index.vue` instead of an approval page. The "合同审批" sidebar link renders the dashboard — dead route.
- KPI endpoint stub at contract.ts:48-62 returns hardcoded zeros. Dashboard KPI cards always show 0. Backend endpoint may or may not exist.

**P1 — Error handling:**
- stores/contract.ts: all 8 async functions use try/finally WITHOUT catch — silent error swallowing. Users get no feedback on failures.
- 7 `(e: any)` catch parameters in approval/detail.vue — violates zero-any policy, unsafe `.message` access.
- 3 `any` types in SidebarMenu.vue (Record<string, any>, return type, variable).
- 11 console.error() in production code (NotificationBell 6, ContractChangeList 4, alertStore 1).
- stores/user.ts: 3 localStorage catch blocks silently swallow errors (loadUserInfo, persistUserInfo, clearUserInfo). Private browsing/quota exhaustion causes silent data loss.

**P2 — Code quality:**
- ESLint baseline: 39 errors across 17 files (10 any, 23 unused-vars, 3 vue/no-unused-vars, 3 e2e).
- Unused imports: ContractChangeList.vue:284 (getApprovalSteps), cost/summary.vue:2,13 (reactive, CostSubjectSummaryVO), cost/ledger.vue:17 (CostSubjectTreeNode).
- Unused assigned: inventory/purchase-request.vue:48,55 (APPROVAL_STATUS_LABEL/COLOR), project/members.vue:27,31 (ROLE_MAP/COLOR).
- 3 `_item` unused in v-for: purchase/order.vue, subcontract/measure.vue, variation/order.vue.
- Non-401 errors leak raw backend messages to users via request.ts:87-90.
- Unused computed/functions: settlement/index.vue:19 (computeSettlementAmount), org/index.vue:150 (handleCompanyReset).

**P3 — Minor:**
- contract.ts:49 `_params` unused parameter in KPI stub (resolved when D3-002 fixed).

### Positive Confirmations
- Zero `@ts-ignore` directives found
- Zero `as any` casts found
- Zero empty catch blocks (all catches have handlers — message.error or console.error)
- Zero pages bypass API modules — ALL pages use proper `@/api/modules/*` imports
- Prior known issue (cost/ledger.vue:17 direct request import) is STALE — line 17 is actually `import { getCostSubjectTree } from '@/api/modules/costSubject'`
- Prior known issue (cost-target/edit.vue:8 direct request import) is STALE — line 8 is actually `import { getProjectList } from '@/api/modules/project'`
- Router guard correctly enforces auth on all non-public routes
- Token refresh flow with pending queue works correctly (request.ts:55-84)
- WHITE_LIST only contains /login — correct; 404 page uses meta.public:true which guard handles
- vue-tsc: 0 TypeScript errors, vite build: 4457 modules clean

### Patterns / Conventions
- Auth pattern: HttpOnly cookie-based (no JS-visible tokens). request.ts has empty request interceptor — relies entirely on `withCredentials: true` and Set-Cookie from backend.
- Error handling dual-logging anti-pattern: ContractChangeList and NotificationBell both `console.error()` AND `message.error()` for same failures. Should be single-path.
- try/finally without catch is the dominant pattern in stores/contract.ts (8/8 functions). Other stores (settlement, project, costTarget) have proper try/catch/finally — contract store is the outlier.
- ESLint: `_` prefix convention for unused params (`_params`, `_item`, `_value`) does NOT satisfy `@typescript-eslint/no-unused-vars` in current config — all still flagged.

### Issues / Gotchas
- Windows PowerShell: special characters like `@` and `\` in grep patterns cause parse errors — use `-SimpleMatch` for literal searches, avoid regex for path-like patterns.
- `grep` on Windows with `console\.(log|error)` pattern fails with shell error — use PowerShell `Select-String` with `-SimpleMatch` or simplified patterns.
- Stale known issues: prior exploration marked line numbers that shifted or were misinterpreted. Always re-verify file content against expected location.

### Cross-Dimension Notes
- contract.ts KPI stub (D3-002) may be backend-gated: verify `/contracts/kpi` endpoint exists before un-stubbing
- Non-401 error message leak (D3-013) relates to Task 1's GlobalExceptionHandler findings — backend error messages should be user-safe
- ESLint errors in e2e/ files are lower priority but still count toward the 39 baseline

## Task 1: Security & Config Review (2026-06-13)

### Key Findings (13 issues: 1 P0, 3 P1, 3 P2, 6 P3)

**P0 — deploy/.env hardcoded passwords [KNOWN]:**
- deploy/.env:2,5,7,10 — 4 plaintext passwords (root123, cgc123, redis123, admin123). Must be replaced with ${VAR} references matching .env.example pattern.

**P1 — Dockerfile sentry defaults:**
- backend/Dockerfile:54-75 contains useSSL=false, JWT_SECRET=change-me-in-production, DB_PASSWORD=must-be-set. Overridden by docker-compose.prod.yml in intended deployment path, but unsafe if container run standalone.

**P1 — Logout doesn't invalidate refresh token:**
- AuthController.java:58-70 only blacklists access token. Stolen refresh token survives logout for up to 7 days.

**P1 — Dockerfile JDBC URL useSSL=false:**
- backend/Dockerfile:54 has useSSL=false in SPRING_DATASOURCE_URL default. Overridden to true in docker-compose.prod.yml:132.

**P2 — Backend port exposed in docker-compose:**
- docker-compose.prod.yml:145-146 maps 8080 to host. MySQL/Redis/MinIO ports are commented out for internal-only. Backend should match.

**P2 — Token blacklist silently disabled without Redis:**
- TokenBlacklistService.java:16 uses @ConditionalOnBean(StringRedisTemplate.class). In local profile (H2, no Redis), blacklist Bean not created, logout/refresh-rotation silently non-functional.

**P2 — Access token lacks jti claim:**
- JwtUtils.java:37-53 generates tokens without standard jti (JWT ID). Blacklist uses last 32 chars as workaround key.

**P3 — Dev/test configs have hardcoded defaults:**
- application-dev.yml:6,42 and application-test.yml:6,29 use ${ENV:hardcoded} pattern with real-looking defaults (cgc123, minioadmin123).

**P3 — Actuator health unrestricted:**
- nginx.conf:120-127 actuator location block commented out. application-prod.yml:72 show-components:always leaks component health details.

**P3 — nginx server_name wildcard:**
- nginx.conf:38,50 uses server_name _ instead of specific domain.

**P3 — Floating JRE base image tag:**
- backend/Dockerfile:20 uses eclipse-temurin:21-jre without digest pinning.

**P3 — File extension count discrepancy:**
- FileService.java:39-43 has 18 extensions, README claims 20. Documentation inconsistency, not security defect.

**P3 — Dev Redis no password config:**
- application-dev.yml:18-19 missing password field (prod has password: ${REDIS_PASSWORD:}).

### Verified Fixes (Previously Known Issues)
- **P1-02 (AuthorizationDeniedException → 500)**: FIXED. GlobalExceptionHandler.java:37-42 handles AuthorizationDeniedException → 403 FORBIDDEN.
- **nginx SSE proxy_buffering**: FIXED. nginx.conf:112 has proxy_buffering off.
- **deploy/.env gitignored**: CONFIRMED. .gitignore line 30 covers .env pattern.

### Positive Confirmations
- @PreAuthorize: 222 annotations across 38 controllers, full coverage. Only /auth/login and /auth/refresh correctly public.
- CORS: allowCredentials(true) with specific origins per profile (no wildcard). Allowed headers: Authorization, Content-Type, X-Refresh-Token only.
- JWT: 15min access / 7d refresh TTL (dev/prod). Refresh rotation: old RT blacklisted before new issued. Disabled account check in both login() and loginById().
- File upload: extension whitelist (18 types), 50MB limit, businessType regex [A-Za-z0-9_-]+, UUID filenames, tenantId checks on download/delete.
- Cookie security: HttpOnly, SameSite=Strict, Secure in prod, refresh token path-scoped to /api/auth/refresh.
- SSL: TLS 1.2/1.3, Mozilla intermediate ciphers, HSTS 1-year, X-Frame-Options: DENY, X-Content-Type-Options: nosniff.
- Logback: password/token/secret/authorization masked via %replace in ALL profiles.
- SecurityConfig: stateless session, CSRF disabled, BCrypt password encoding, @EnableMethodSecurity.
- Docker: non-root user (appuser), HEALTHCHECK on all 5 services, prod Swagger disabled, Flyway clean-disabled.
- .env.example: clean — all security values empty, JWT_SECRET generation instructions included.

### Patterns / Conventions
- JWT claims stored in JwtUtils constants (CLAIM_USER_ID, CLAIM_TOKEN_TYPE, etc.) — consistent reference pattern
- ENV_VAR:default pattern used consistently across all application-*.yml files — good, but dev/test defaults are real-looking
- Token resolution priority: cookie → Authorization header — CookieUtils + JwtAuthenticationFilter use same order
- JSON error responses (ApiResponse) for 401/403 rather than redirect to login page — API-appropriate

### Issues / Gotchas
- @ConditionalOnBean(StringRedisTemplate.class) creates silent failure mode — Bean absence = feature absence, no warning
- Dockerfile ENV defaults serve dual purpose: documentation AND runtime fallback — dangerous for security values
- TokenBlacklistService tokenKey() uses substring — works but non-standard; jti would be standards-compliant
- Refresh token cookie path /api/auth/refresh is correct but fragile — if context-path changes, breaks silently

## Task 4: Data & Infrastructure Review (2026-06-13)

### Key Findings (15 total: 3 P0, 5 P1, 6 P2, 1 P3)

**P0 — Blocking:**
- **deploy/.env**: 5 hardcoded weak passwords (root123, cgc123, redis123, admin123) + JWT_SECRET entirely missing. Backend won't start in prod profile.
- **Backup doc hardcoded passwords**: All example commands use root123/redis123/admin123. Copy-paste risk into production.
- **docker-compose.prod.yml useSSL=true**: MySQL auto-generates self-signed certs. Connector/J may reject them. Dockerfile says useSSL=false — conflicting defaults.

**P1 — High:**
- **Zero FK constraints** across all 54 tables. App-layer integrity only.
- **Backup doc infra gap**: 4 scripts + 2 config dirs referenced but don't exist.
- **Backup doc Flyway count**: Says 40 (V1-V40), actual is 46 (V1-V46).
- **CI deploy no health/rollback**: docker pull + compose up only, no verification.
- **Dockerfile SSL inconsistency**: ENV says false, compose overrides to true.

**P2 — Medium:**
- **14 FK columns missing indexes**: partner_id on 5 tables most critical.
- **Frontend nginx as root**: No USER directive (backend has appuser).
- **CI flyway-check**: 60s timeout + fragile grep + no cleanup trap.
- **Frontend COPY public/ commented out**: May miss static assets.
- **V42 mat_warehouse seed naming**: created_time in V42 INSERT (V45 fixes).
- **CI test passwords plaintext**: test/test in yaml (low risk, ephemeral CI).

**P3 — Low:**
- **docker-compose version: "3.8" deprecated**: Ignored by Compose V2.

---

## Session Closure — 2026-06-13

**Phase Complete**: Review phase (Tasks 0-7) — all 7 tasks done.
**Remaining**: F1-F4 blocked on fix execution (56 findings need repair before final verification).
**Next Action**: Execute Wave 3 fix tasks per synthesis report, then run F1-F4 verification gate.
**Deliverable**: `.sisyphus/evidence/synthesis-report.md` is the actionable fix plan with 56 deduplicated findings grouped into 6 executable waves (3-A through 3-F).

## F2: Frontend Build + ESLint Baseline (2026-06-13)

### Build Results
- **TypeScript errors: 0** — `vue-tsc --noEmit` passed cleanly ✅
- **Vite build: PASS** — Exit code 0, 4457 modules transformed in 13.05s
- **Dist file count: 92** (1 index.html + 2 fonts + 32 CSS + 57 JS)
- Evidence: `.sisyphus/evidence/task-f2-frontend-build.txt`

### ESLint Results
- **39 errors, 0 warnings** — Exit code 1 (as expected)
- Evidence: `.sisyphus/evidence/task-f2-eslint.txt`

#### Error Breakdown
| Category | Count | Notes |
|----------|-------|-------|
| `@typescript-eslint/no-explicit-any` | 12 | notification.spec.ts:2, SidebarMenu.vue:3, approval/detail.vue:7 |
| `@typescript-eslint/no-unused-vars` | 23 | Across 15 files (src/ + e2e/) |
| `vue/no-unused-vars` | 4 | purchase-request.vue, purchase/order.vue, subcontract/measure.vue, variation/order.vue |
| **Total** | **39** | |

#### Files with Errors (17 files)
- **e2e/ (3 files, 5 errors)**: inventory.spec.ts:2, invoice.spec.ts:1, notification.spec.ts:2
- **src/api/modules/ (2 files, 2 errors)**: contract.ts:1, inventory.ts:1
- **src/components/ (2 files, 2 errors)**: ContractChangeList.vue:1, NotificationBell.test.ts:1
- **src/layouts/ (1 file, 3 errors)**: SidebarMenu.vue:3 (all no-explicit-any)
- **src/pages/ (8 files, 25 errors)**: 
  - approval/detail.vue:7 (all no-explicit-any)
  - approval/todo.vue:1
  - cost-target/index.vue:1
  - cost/ledger.vue:1
  - cost/summary.vue:2
  - inventory/purchase-request.vue:3
  - invoice/index.vue:1
  - org/index.vue:3
  - project/members.vue:2
  - purchase/order.vue:1
  - settlement/index.vue:1
  - subcontract/measure.vue:1
  - variation/order.vue:1
- **src/stores/ (1 file, 2 errors)**: user.ts:2

### Known Baseline Comparison
| Metric | Expected | Actual | Match |
|--------|----------|--------|-------|
| TS errors | 0 | 0 | ✅ |
| ESLint errors | 39 | 39 | ✅ |
| ESLint warnings | 0 | 0 | ✅ |
| Build time | ~14s | 13.05s | ✅ |
| Modules | ~4457 | 4457 | ✅ |
| `no-explicit-any` | 10 | 12 | ⚠️ +2 vs prior report |

**Discrepancy**: Known baseline doc says 10 `no-explicit-any`, actual count is 12 (notification.spec.ts has 2 that may have been miscounted previously). Total 39 matches — the distribution shifted slightly but overall unchanged.

### Verdict
- **Pre-fix baseline captured**. Build PASS (0 TS errors), ESLint at 39 errors / 0 warnings.
- Ready for F2 re-run after ESLint fixes (M-014 through M-018) are applied.

---

## BLOCKER — F1-F4 Gated on Fix Execution

| Gate | Depends On | Current State |
|------|-----------|---------------|
| F1: Backend regression | All P0/P1 fixes + new TDD tests | 206/206 pass (pre-fix baseline) |
| F2: Frontend build + ESLint | Frontend P0/P1 fixes (M-005, M-006, M-014-M-018) | 0 TS errors, 39 ESLint errors |
| F3: E2E smoke tests | Running dev env + all fixes | Not run (baseline unknown) |
| F4: Dimension re-scan | All fixes completed | 56 findings open |

**Resolution**: Execute Wave 3-A (6 P0) + Wave 3-C (17 P1) → then F1-F4 can proceed.
**Plan status**: Review phase (0-7) delivered. Fix phase (8-N) deferred. Verification (F1-F4) blocked.

### Verified Resolved
- Flyway: 46 MySQL + 46 H2 = SYNC
- MATERIAL_CLERK/FINANCE roles: Both have sys_role_menu entries in V42
- mat_purchase_request_item.material_id: Index exists (V35) + V46 guard
- Nginx SSE: proxy_buffering off configured
- V45 unifies all 16 tables' audit columns

### Patterns
- Naming split: V1-V21 uses created_at, V22-V38 uses created_time, V45 unified all to created_at
- MySQL idempotency: INSERT IGNORE INTO; H2: INSERT INTO ... WHERE NOT EXISTS
- V46 MySQL: dynamic SQL (information_schema + PREPARE/EXECUTE); H2: CREATE INDEX IF NOT EXISTS
- Zero FOREIGN KEY constraints — deliberate application-layer referential integrity

## Task 6: Fix Regression Verification (2026-06-13)

### Key Findings (3 issues, all P3 — no blocking)

**D6-001 (P3) — @SuppressWarnings count +1 from baseline:**
- Baseline: 4 (2 main + 2 test). Current: 5 (3 main + 2 test).
- Phase4IntegrationTest.java:762,786 added 2 new @SuppressWarnings("unused") on test helper methods.
- Main source unchanged at 3 (UserContext:25, PayApplicationService:67, MatReceiptService:471).
- Test-only, no production impact.

**D6-002 (P3) — Misleading "ignored" catch variable naming:**
- 3 catch blocks name exception variable "ignored" but actually log it:
  - WorkflowCoreService.java:230: `catch (Exception ignored) { log.error(..., ignored); }`
  - NotificationService.java:163: `catch (Exception ignored) { log.warn(..., ignored); }`
  - MatReceiptService.java:481: `catch (Exception ignored) { log.warn(..., ignored); }`
- These are NOT empty catch blocks (exception IS logged), but variable naming is misleading.
- Pre-existing issue, not regression from audit-fixes.

**D6-003 (P3) — 11 test files modified, no regressions:**
- 8 backend test files + 3 frontend test files changed in audit-fixes scope.
- Backend: 206/206 tests pass at HEAD.
- Frontend: 0 TypeScript errors, pnpm build passes.

### Verified Fixes (All Major Architectural Changes PASS)

**Check 3 — WorkflowEngine Split:**
- Old WorkflowEngine.java (136 lines) is now a pure facade with zero business logic.
- Delegates to 7 focused services: WorkflowSubmitService, WorkflowApprovalService, WorkflowTaskService, WorkflowWithdrawService, WorkflowCoreService, WorkflowQueryService, WfCcService.
- All services have clean single-responsibility boundaries.

**Check 4 — DateTimeUtils Extraction:**
- All DateTimeFormatter.ofPattern() centralized in DateTimeUtils.java (DTF, DATE_FMT, DATE_COMPACT).
- ZERO remaining direct DTF definitions outside DateTimeUtils.java.
- Former 27× duplication fully eliminated.

**Check 5 — CostSubjectResolver:**
- 4 strategies use shared CostSubjectResolver (ContractCostStrategy, SubMeasureCostStrategy, MaterialReceiptCostStrategy, CtContractChangeCostStrategy).
- CtContractChangeCostStrategy correctly uses resolveForChange() with 4-tier fallback (变更→合同→root→any).
- VarOrderCostStrategy uses item.getCostSubjectId() directly — intentional design (VarOrderItem carries explicit costSubjectId from user input).
- Zero duplicated resolution logic.

**Check 6 — Dashboard N+1 Fix:**
- getManagementView() batch-fetches ALL projects, then calls costSummaryService.getBatchProjectSummaries() BEFORE the loop.
- Loop uses Map lookup (summaryMap.get(project.getId())) — zero per-iteration DB queries.
- batchLoadInstances() helper uses selectBatchIds() for WfInstance enrichment.

**Check 7 — Mass Assignment Protection:**
- BaseEntity protects 6 fields: createdBy, createdAt, updatedBy, updatedAt, deletedFlag, remark (all @JsonProperty(READ_ONLY)).
- PayApplication: tenantId, id, approvalStatus protected.
- PayInvoice: tenantId, id, invoiceStatus, verifyStatus, approvalStatus, deletedFlag protected.
- PayRecord: tenantId, id protected.
- Service-layer defense: all services override tenantId from UserContext before persistence.

**Check 8 — Input Validation:**
- All 31 controllers have @Valid on entity @RequestBody parameters (79 total annotations).
- PayInvoice: @NotBlank on invoiceNo, invoiceCode; @NotNull on invoiceDate.
- PayApplication: @NotNull on contractId, projectId; @Positive on amount.
- PayRecord: @NotNull on contractId, amount.

**Check 9 — Regression Scan:**
- console.log in frontend src/: ZERO (verified).
- Hardcoded secrets in changed backend files: ZERO (pattern scan for password/secret/token).
- Empty catch blocks: 0 truly empty (3 "ignored" blocks actually log).
- @SuppressWarnings: +1 in test only (see D6-001).

**Check 10 — Test Impact:**
- 8 backend test files + 3 frontend test files changed.
- Backend: 206/206 pass. Frontend: 0 TS errors.
- No test regressions.

### Positive Confirmations
- StlSettlementService duplicate guard: FIXED at line 116-122 (STL_DUPLICATE_SETTLEMENT).
- WorkflowController PURCHASE_REQUEST case: FIXED at line 78 (P1-04).
- warrantyRate calculation: FIXED — uses movePointLeft(2) (P1-01 via commit c6eebbc).
- paidAmount + settlementAmount in CtContractVO: FIXED (P2-01/P2-02 via commit 6f39714).
- contractId in AlertLog: FIXED (P2-03 via commit 6b3157c).
- Settlement sources endpoint: FIXED (P2-04 via commit 6815ec5).
- Seed data for materials/warehouses/cost subjects: FIXED (P2-05/P2-06/P3-04 via commit 6486ec4).
- Double /api prefix: FIXED (P3-01 via commit c6c7a93).
- Frontend as any eliminated: FIXED (commit 541d292).
- Frontend console.error logging: FIXED (commit 97106cd).

### Issues / Gotchas
- GIT_MASTER=1 env var prefix doesn't work on Windows PowerShell (Unix shell syntax). Must use `$env:GIT_MASTER='1'; git ...` pattern.
- grep -G with pipe characters (|) fails in PowerShell — use Select-String -SimpleMatch for literal searches.
- The `rg` (ripgrep) `--include` flag is not the GNU grep `--include` — on Windows use Select-String patterns.
- PowerShell MeasurementObject for unique file count: must Trim() each line before Sort-Object -Unique.

### Verdict
ALL 10 verification checks PASS. Zero regressions detected. 3 P3 findings are documentation/code-quality observations only — none block release.

---

## Wave 2: Synthesis Report (2026-06-13)

### Key Results
- **72 raw findings** across 6 dimensions → **56 unique** after dedup (22% dedup rate)
- **6 P0** (blocking), **17 P1** (must-fix sprint 1), **26 P2** (should fix), **7 P3** (nice-to-fix)
- **Beta gate**: BLOCKED — P0=6 (target 0), P1=17 (target ≤3)
- **Zero hard dependencies** among all P0/P1 findings — all fixes independently executable

### Cross-Dimension Dedup Actions
- **M-001**: D1-001 + D4-001 merged (deploy/.env passwords + missing JWT_SECRET)
- **M-003**: D4-003 + D4-008 merged (useSSL config conflict)
- **M-007**: D1-002 + D1-004 merged (Dockerfile sentinel defaults including useSSL)
- **M-023**: D5-001 + D5-002 merged (contractAmount→currentAmount — same root cause)
- **M-027**: D2-007 + D2-008 + D2-009 + D6-002 merged (misleading "ignored" catch variables, 3 instances)
- **D6-003**: Informational only — excluded from master list (no actionable fix needed)

### Cross-Dimension Conflicts Resolved
1. **VarOrderCostStrategy** (D2: correct by design vs D5: missing fallback): Both valid. D2/D6 correct that design is intentional (VarOrderItem carries explicit costSubjectId). D5 correct that null costSubjectId has no fallback unlike other 3 strategies. Recommendation: add `resolveForChange()` as fallback when null — defense-in-depth, severity stays P2.
2. **writeRecord() naming vs behavior** (D2-007 vs D5-004): Split into two findings — M-027 (naming) and M-028 (behavioral consequence: tenant_id=0 on lookup failure).

### Fix Wave Design
- **Wave 3-A**: 6 independent P0 fixes (deploy/.env, backup doc, useSSL, @Transactional, route mismatch, KPI stub)
- **Wave 3-B**: 0 dependent P0 fixes
- **Wave 3-C**: 17 independent P1 fixes (payment amount, N+1 ×4, Mass Assignment, try/finally, (e:any), any types, console.error, localStorage, FK constraints, backup infra, doc counts, CI deploy)
- **Wave 3-D**: 0 dependent P1 fixes
- **Wave 3-E**: 26 independent P2 fixes
- **Wave 3-F**: 7 P3 polish items

### Beta Gate Projection
- **Currently BLOCKED**: P0=6 (target 0), P1=17 (target ≤3)
- **After Wave 3-A + 3-C**: P0=0 ✅, P1=0 ✅, tests=206/206 ✅, build=0 TS errors ✅ → **READY**

### Verified Fixes Compiled (16 items)
- AuthorizationDeniedException→403, nginx SSE buffering off, deploy/.env gitignored, settlement contractId guard, warrantyRate movePointLeft(2), PURCHASE_REQUEST case, PayApplicationController @Valid, InvoiceController @Valid, MATERIAL_CLERK/FINANCE sys_role_menu, mat_purchase_request_item index, frontend as any eliminated, console.error logging, paidAmount/settlementAmount in CtContractVO, contractId in AlertLog, double /api prefix, seed data

### Architectural Changes Verified (7 items)
- WorkflowEngine split (7 services), DateTimeUtils centralized, CostSubjectResolver deduplication, Dashboard N+1 fix, Mass Assignment BaseEntity protection, Input Validation (@Valid on all 31 controllers), Flyway parity (46+46)

## F1: Backend Regression Test Baseline (2026-06-13)

### Test Results
- **Tests run: 206, Failures: 0, Errors: 0, Skipped: 0** — 100% pass
- **BUILD SUCCESS** — Total time: 37.945 s
- Profile: `local` (H2 in-memory)
- Evidence: `.sisyphus/evidence/task-f1-backend-baseline.txt`

### Code Smell Scan
- **@SuppressWarnings in main source: 3** (unchanged from baseline)
  - `UserContext.java:25` — `@SuppressWarnings("unchecked")`
  - `MatReceiptService.java:471` — `@SuppressWarnings("unchecked")`
  - `PayApplicationService.java:67` — `@SuppressWarnings("java:S107")`
- **"ignored" catch blocks: 3** (unchanged from baseline; all actually log, naming is misleading)
  - `WorkflowCoreService.java:230` — `catch (Exception ignored) { log.error(...) }`
  - `NotificationService.java:163` — `catch (Exception ignored) { log.warn(...) }`
  - `MatReceiptService.java:481` — `catch (Exception ignored) { log.warn(...) }`
- **Zero truly empty catch blocks** — all catch blocks have logging or handling

### Verdict
- **No regressions detected**. Baseline matches known state (206/206, 3 @SuppressWarnings, 3 "ignored" catches).
- Pre-fix snapshot captured. Ready for F1 re-run after Wave 3 fixes complete.

## F3: E2E Infrastructure Check (2026-06-13)

### Test Listing Results
- **Command**: `npx playwright test --list` — exit code 0 (SUCCESS)
- **Spec files: 10** — accessibility, approval, contract, dashboard, inventory, invoice, login, notification, procurement, settlement
- **Total tests: 41** — all syntactically valid (listing succeeded)
- **Browser**: chromium
- Evidence: `.sisyphus/evidence/task-f3-e2e-check.txt`

### Per-Spec Breakdown
| Spec | Tests |
|------|-------|
| accessibility.spec.ts | 5 |
| approval.spec.ts | 3 |
| contract.spec.ts | 2 |
| dashboard.spec.ts | 5 |
| inventory.spec.ts | 5 |
| invoice.spec.ts | 5 |
| login.spec.ts | 2 |
| notification.spec.ts | 5 |
| procurement.spec.ts | 5 |
| settlement.spec.ts | 4 |

### Discrepancy
- **Test count: 41 actual vs 36 documented** in README. (+5 tests). README may be outdated — actual test count is 41 across 10 spec files.

### Notes
- No syntax/parse errors — all 41 tests passed the listing phase
- Full E2E execution (with running servers) deferred to post-fix phase
- Previous attempt to start backend via `Start-Process` failed due to working-directory mismatch (mvnw needs to run from `backend/` directory)
- `Start-Job` with script block also proved unreliable for capturing Maven output on Windows PowerShell
- For future: use `Start-Process -WorkingDirectory backend -FilePath "D:\...\backend\mvnw.cmd"` with absolute paths

## F4: Pre-Fix Dimension Confirmation Scan (2026-06-13)

### Scan Results
- **13/13 findings CONFIRMED PRESENT** across 5 dimensions (D1-D5, D6 skipped)
- **0 unexpected-absent** — no fixes applied between review and now
- Evidence: `.sisyphus/evidence/task-f4-prescan.txt`

### Per-Dimension Breakdown

| Dimension | Findings | Verdict |
|-----------|----------|---------|
| D1 Security | M-001, M-007, M-008 | All PRESENT |
| D2 Backend Code | M-004, M-009, M-010 | All PRESENT |
| D3 Frontend | M-005, M-006, M-014 | All PRESENT |
| D4 Data/Infra | M-002, M-003, M-019 | All PRESENT |
| D5 Business Logic | M-023 | PRESENT |

### Key Confirmations
- **M-001**: 4 hardcoded passwords in deploy/.env (root123:2, cgc123:5, redis123:7, admin123:10) + missing JWT_SECRET
- **M-004**: @Scheduled at CostSummaryService:395 — self-invocation bypass still present
- **M-005**: ContractApproval route (router/index.ts:37-38) still maps to `@/pages/dashboard/index.vue`
- **M-006**: KPI stub in contract.ts:47-62 — TODO comment + hardcoded zeros
- **M-014**: stores/contract.ts — all 8 async functions use `try {} finally {}` with zero `catch` blocks
- **M-019**: Zero FOREIGN KEY in 46 migration files (grep returned 0 matches)
- **M-023**: PayApplicationService:368 uses `getContractAmount()` not `getCurrentAmount()`

### Patterns / Gotchas
- **Windows grep pipe issue**: Patterns with `|` (e.g., `root123|cgc123`) are interpreted as shell pipe operators by PowerShell. Solution: use `read` tool for file content, or use PowerShell `Select-String -SimpleMatch` for literal matches.
- **grep regex escaping**: `{` is a regex meta-character — patterns like `try {` need escaping as `try \{` in grep. Better: use `read` tool on small files.

### Verdict
- Pre-fix baseline established. All 13 top-findings verified present at HEAD.
- Ready for post-fix re-scan (compare against this baseline after Wave 3 fixes complete).
