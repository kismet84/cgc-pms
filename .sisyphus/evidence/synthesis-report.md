# Synthesis Report — Wave 2 Cross-Dimension Consolidation

**Date**: 2026-06-15
**Sources**: 6 dimension review reports (D1–D6) executed on 2026-06-15
**Method**: Cross-dimension deduplication → P-grading → dependency mapping → fix wave planning
**Output**: `synthesis-report.md` + appended T8–T21 fix tasks to `consolidated-remaining-work.md`

---

## 1. Executive Summary

| Metric | Value |
|--------|-------|
| Source reports | 6 (`review-1` through `review-6`) |
| Raw findings across all dimensions | 73 (15 + 14 + 14 + 15 + 12 + 0) |
| Cross-dimension merges | 6 merge groups (14 raw → 6 consolidated) |
| **Consolidated P0** | **3** (2 Frontend, 1 Backend — blocks production) |
| **Consolidated P1** | **14** (5 Backend, 4 Frontend, 3 Infra/Deploy, 2 Business) |
| P2 catalogue (no fix tasks) | 25 (7 Security, 5 Backend, 4 Frontend, 7 Infra, 2 Business) |
| P3 catalogue (no fix tasks) | 28 |
| Regression findings (D6) | 0 (all 10/10 checks pass) |
| **Fix tasks generated** | **14** (T8–T21 for all P0 + P1) |

### Grade Definitions Applied

| Grade | Criteria | Action |
|-------|----------|--------|
| **P0** | Blocks production deployment: security holes, data corruption, crash | Fix immediately — TDD task |
| **P1** | High priority, should fix before launch: TOCTOU races, missing validation, nginx misconfig, N+1 | Fix in this wave — TDD task |
| **P2** | Medium, fix in first sprint after launch: code smells, oversized classes, missing indexes, ESLint | Catalogue only — backlog |
| **P3** | Low/cosmetic: deprecated versions, naming inconsistencies, documentation | Catalogue only — backlog |

---

## 2. Cross-Dimension Deduplication Map

### Merge Groups (6 merges)

| Merge ID | Raw IDs | Files Involved | Resolution |
|----------|---------|----------------|------------|
| M1 | D1-009 (P2) + D4-007 (P1) | `backend/Dockerfile:54-75` | **Merged → P1**. D1 reviewer graded P2 but description states "critical vulnerability" for empty JWT_SECRET. D4's P1 is correct. Unified as **S-P1-10**. |
| M2 | D1-002 (P1) + D1-010 (P2) | `deploy/docker-compose.prod.yml:133`, `backend/Dockerfile:54`, `docker-compose.dev.yml:117` | **Merged → P1**. D1-010 is the superset documenting the same `useSSL=false` pattern across all Docker configs. Unified as **S-P1-02**. |
| M3 | D2-001 (P1) + D2-004 (P1) | `PayApplicationService.java:139,471-483` | **Merged → P1**. D2-004 (wrong toVO overload) is the root cause of D2-001 (N+1 symptom). Same file, same method. Unified as **S-P1-05**. |
| M4 | D3-004 (P1) + D3-005 (P1) | `pages/invoice/index.vue:265,322`, `pages/system/users/index.vue:131` | **Merged → P1**. Same anti-pattern (`catch (e: any)`), two files. Single fix strategy. Unified as **S-P1-09**. |
| M5 | D5-001 (P0) + D5-002 (P1) | `StlSettlementService.java:162-188` | **Related, kept separate**. Same method, same file, but different races (contractId uniqueness vs code generation). Sequenced for same fix wave. S-P0-03 and S-P1-12. |
| M6 | D1-001 (P1) + D4-015 (P1) | `deploy/.env.example`, `deploy/.env` | **Related, kept separate**. Different files, different concerns (template quality vs filesystem security). S-P1-01 and S-P1-11. |

### Non-Merged Cross-References (Related but Distinct)

| Pair | Why Not Merged |
|------|----------------|
| D1-003 (P1 HSTS) ↔ D1-011 (P2 CSP) | Different headers, different nginx locations. Related but distinct fixes. |
| D1-006 (P2 Spring headers) ↔ D1-014 (P3 XSS disabled) | Same file (SecurityConfig.java), different severity and scope. |
| D4-001 (P2 SMALLINT) ↔ D4-002 (P2 unnamed constraint) | Same V47 migration file, different bugs (type vs naming). |

---

## 3. Consolidated Findings — P0 (BLOCKS PRODUCTION)

### S-P0-01 | D3-001 | Raw axios bypass — system/data page

- **Source**: D3 (Frontend) finding #001
- **File**: `frontend-admin/src/pages/system/data/index.vue:4,19`
- **Issue**: Imports raw `service` from `@/api/request` (bypassing typed API module layer). Calls `service.delete()` directly — skips response interceptor data unwrapping. Uses `: any` on response. The `res?.data` double-unwraps (interceptor already unwrapped).
- **Impact**: Breaks API contract. If interceptor changes, this page silently breaks. Creates inconsistency with all other pages that use API modules.
- **Fix**: Create `api/modules/system.ts` method `clearDatabase()`, import and use it. Remove `: any`. Fix double-unwrap.
- **Dependencies**: None (standalone frontend fix)

### S-P0-02 | D3-002 | API module bypass — profile page

- **Source**: D3 (Frontend) finding #002
- **File**: `frontend-admin/src/pages/profile/index.vue:5,33,67`
- **Issue**: Imports `request` directly from `@/api/request`, bypassing API module layer. Inline URL construction for `PUT /profile` and `PUT /profile/password`.
- **Impact**: Same as S-P0-01 — contract bypass, maintainability risk, inconsistency.
- **Fix**: Move to `api/modules/user.ts` (or create `profile.ts`). Methods: `updateProfile(data)` and `changePassword(data)`.
- **Dependencies**: None (standalone frontend fix)

### S-P0-03 | D5-001 | Settlement contractId TOCTOU race

- **Source**: D5 (Business Logic) finding #001
- **File**: `backend/src/main/java/com/cgcpms/settlement/service/StlSettlementService.java:162-169`
- **Issue**: Check-then-insert pattern for contractId uniqueness. Two concurrent POSTs both see `existingCount == 0` and both insert. No DB-level UNIQUE constraint on `(tenant_id, contract_id)`. Only `uk_stl_settlement_code` exists on `(tenant_id, settlement_code)`.
- **Impact**: **Data corruption** — duplicate settlements for same contract. Financial data integrity compromised.
- **Fix**:
  1. Create Flyway V52 migration: `ALTER TABLE stl_settlement ADD CONSTRAINT uk_stl_tenant_contract UNIQUE (tenant_id, contract_id)`
  2. Wrap insert in try-catch for `DuplicateKeyException` → throw `BusinessException("STL_DUPLICATE_SETTLEMENT")`
  3. Write integration test: concurrent creates → one succeeds, one gets 409
- **Dependencies**: Requires Flyway migration. May need DB reset if duplicate data already exists.

---

## 4. Consolidated Findings — P1 (HIGH PRIORITY, FIX BEFORE LAUNCH)

### 4.1 Security & Configuration (4 findings)

#### S-P1-01 | D1-001 | deploy/.env.example self-referencing placeholders

- **File**: `deploy/.env.example:3,6,8,12,17`
- **Issue**: All 5 secret values use `${VAR_NAME}` self-references. Docker Compose does not recursively expand these; the literal string `${MYSQL_ROOT_PASSWORD}` is passed to containers as the password value.
- **Impact**: Inexperienced deployer gets silently insecure deployment with literal `${...}` strings as passwords.
- **Fix**: Replace with explicit placeholder strings: `CHANGE-ME-ROOT-PASSWORD`, `CHANGE-ME-JWT-SECRET-32CHARS-MIN`, etc.
- **Dependencies**: Related to S-P1-11 (deploy/.env plaintext). Can be same task.

#### S-P1-02 | D1-002 + D1-010 | useSSL=false in all Docker JDBC URLs

- **Files**: `deploy/docker-compose.prod.yml:133`, `backend/Dockerfile:54`, `deploy/docker-compose.dev.yml:117`
- **Issue**: All Docker configurations hardcode `useSSL=false` in JDBC URLs, overriding `application-prod.yml`'s secure default of `useSSL=true`. Database traffic on Docker bridge/overlay networks is unencrypted.
- **Impact**: Defense-in-depth gap. In multi-tenant or regulated deployments, database traffic is interceptable.
- **Fix**: Replace hardcoded `useSSL=false` with env variable `SPRING_DATASOURCE_URL: "...useSSL=${DB_USE_SSL:-true}"` across all three files plus Dockerfile.
- **Dependencies**: Touches same files as S-P1-10. Can be same task.

#### S-P1-03 | D1-003 | HSTS commented out; no HTTP→HTTPS redirect

- **File**: `frontend-admin/nginx.conf:107` (HTTPS block), `lines 36-80` (HTTP block)
- **Issue**: `add_header Strict-Transport-Security` is commented out. HTTP server block serves content without redirecting to HTTPS. A deployer using this config verbatim gets no HSTS and plain HTTP.
- **Impact**: Users can access site over insecure HTTP. No browser HSTS enforcement.
- **Fix**: (1) Uncomment HSTS with `max-age=31536000; includeSubDomains`. (2) Replace HTTP server block content with `return 301 https://$host$request_uri;`.
- **Dependencies**: Related to D1-011 CSP (P2). Can be same nginx security task.

#### S-P1-04 | D1-004 | Auth login/refresh endpoints lack rate limiting

- **File**: `backend/src/main/java/com/cgcpms/auth/controller/AuthController.java:42,79`
- **Issue**: `/auth/login` and `/auth/refresh` are public endpoints with no rate limiting, account lockout, or CAPTCHA. Brute-force attacks are unthrottled.
- **Impact**: Attacker can dictionary-attack passwords without any throttling.
- **Fix**: Add rate limiter (5 attempts/min per IP+username). Choose: Redis-based (existing infra) or Bucket4j/Resilience4j. Consider account lockout after N failures.
- **Dependencies**: Requires rate-limiting library dependency decision.

### 4.2 Backend Code & Architecture (3 findings)

#### S-P1-05 | D2-001 + D2-004 | N+1 Queries in PayApplicationService.toVO()

- **File**: `backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java:139,471-483`
- **Issue**: `getById()` calls the single-arg `toVO(PayApplication)` overload which performs 3 individual `selectById` calls (project, contract, partner). A batch overload with pre-fetched maps already exists and is used by `getPage()`. `getById()` calls the wrong one.
- **Impact**: Every `GET /pay-applications/{id}` generates 3 unnecessary DB queries.
- **Fix**: Refactor `getById()` to use the pre-fetch pattern (collect IDs → batch select → build map → toVO with map). Deprecate single-arg overload or make it private.
- **Dependencies**: Touches same file that S-P1-07 (readOnly) would also touch.

#### S-P1-06 | D2-002 | Empty catch block in InvoiceService

- **File**: `backend/src/main/java/com/cgcpms/invoice/service/InvoiceService.java:210`
- **Issue**: `catch (Exception ignored) { // ignore close errors }` — zero logging. PDF document close failures are impossible to diagnose.
- **Impact**: Silent resource leaks. Production debugging impossible.
- **Fix**: Add `log.debug("Failed to close PDF document", e)` at minimum.
- **Dependencies**: None (single-line fix).

#### S-P1-07 | D2-003 | Zero services use @Transactional(readOnly=true)

- **Scope**: All 42+ service files with `@Transactional`. Zero query methods (getPage, getById, list, etc.) use `readOnly=true`.
- **Issue**: Missing `readOnly=true` prevents documentation of developer intent and potential JDBC/Hibernate flush optimizations.
- **Impact**: Suboptimal DB performance. No flush-skip optimization for read operations.
- **Fix**: Audit all service methods. Add `@Transactional(readOnly = true)` to read-only methods. Keep `readOnly=false` (default) on write methods.
- **Dependencies**: Touches 42+ files — large blast radius. Should be the LAST backend P1 task to avoid merge conflicts.

### 4.3 Frontend & API (2 findings)

#### S-P1-08 | D3-003 | 122 catch blocks lose error context (no parameter)

- **Scope**: 122 instances across 37 files in `pages/`, `stores/`, `components/`, `api/`
- **Pattern**: `} catch {` (valid JS, but discards the error object entirely)
- **Worst files**: `pages/org/index.vue` (9), `pages/payment/index.vue` (8), `stores/contract.ts` (8), `pages/invoice/index.vue` (6), `pages/system/dict/index.vue` (6)
- **Impact**: Silent failures. Users see empty lists with no error feedback. Impossible to debug in production.
- **Fix**: Add `(e: unknown)` parameter to all 122 catches. At minimum: `if (import.meta.env.DEV) console.error('context:', e)`. For user-visible failures, `message.error('加载失败')`.
- **Dependencies**: Touches 37 files — conflicts with any frontend change (S-P0-01, S-P0-02, S-P1-09, etc.). Must go LAST among frontend fixes.

#### S-P1-09 | D3-004 + D3-005 | catch (e: any) type assertion anti-pattern

- **Files**: `pages/invoice/index.vue:265,322`, `pages/system/users/index.vue:131`
- **Issue**: `catch (error: any)` and `catch (err: any)` with unchecked property access (`error?.response?.data?.message`). Violates TypeScript strict mode.
- **Impact**: Unsafe type assertions. Runtime errors if error shape differs.
- **Fix**: Replace with `catch (e: unknown)` + `instanceof Error` check or safe property access. For axios errors: use `axios.isAxiosError(e)` guard.
- **Dependencies**: 2 files — small blast radius. Can be done before S-P1-08.

### 4.4 Infrastructure & Deploy (2 findings)

#### S-P1-10 | D4-007 + D1-009 | Dockerfile empty credential defaults

- **File**: `backend/Dockerfile:54-75`
- **Issue**: `ENV JWT_SECRET=`, `ENV DB_PASSWORD=`, `ENV SPRING_DATA_REDIS_PASSWORD=`, `ENV MINIO_SECRET_KEY=` — all empty. Running `docker run cgc-pms-backend` starts with blank secrets. Empty JWT_SECRET is a critical security vulnerability.
- **Impact**: Standalone container runs with zero security. JWT tokens signed with empty key.
- **Fix**: Replace empty defaults with sentinel values + `@PostConstruct` startup validation:
  ```
  ENV JWT_SECRET=__MUST_OVERRIDE_IN_PRODUCTION__
  ENV DB_PASSWORD=__MUST_OVERRIDE_IN_PRODUCTION__
  ```
  Add startup check that fails fast if any secret is still the sentinel.
- **Dependencies**: Same file as S-P1-02 (useSSL). Can be one "backend Dockerfile hardening" task.

#### S-P1-11 | D4-015 | deploy/.env stores production credentials in plaintext

- **File**: `deploy/.env` (gitignored but filesystem-exposed)
- **Issue**: While `.gitignore` excludes the file from git, any filesystem compromise exposes all secrets (MySQL root password, JWT_SECRET, Redis password, MinIO credentials). No encryption at rest.
- **Impact**: Single file exfiltration = complete credential compromise.
- **Fix**: Add `chmod 600 deploy/.env` to deployment docs. Consider Docker secrets or vault for production. At minimum, document the risk.
- **Dependencies**: Related to S-P1-01 (.env.example fix). Can be same task.

### 4.5 Business Logic (3 findings)

#### S-P1-12 | D5-002 | Settlement code sequence TOCTOU race

- **File**: `backend/src/main/java/com/cgcpms/settlement/service/StlSettlementService.java:174-188`
- **Issue**: Settlement code generation (`STL-yyyyMMdd-NNN`) uses SELECT+INSERT pattern. Two concurrent creates at same millisecond may compute same sequence number. `DuplicateKeyException` on `uk_stl_settlement_code` is not caught → 500 Internal Server Error.
- **Impact**: Race window causes 500 errors for concurrent settlement creation. Not data corruption, but service unavailability.
- **Fix**: Wrap code generation + insert in retry loop on `DuplicateKeyException`. Or use Redis INCR / DB sequence for atomic code generation.
- **Dependencies**: Same method as S-P0-03. Can be fixed in the same task (P0 settlement TOCTOU fix).

#### S-P1-13 | D5-003 | Settlement finalization lacks optimistic lock

- **File**: `backend/src/main/java/com/cgcpms/settlement/handler/SettlementWorkflowHandler.java:106-118`
- **Issue**: `onApproved()` uses `LambdaUpdateWrapper` with only ID guard. No version or status check. If two workflow approvals complete concurrently (countersign race), both callbacks execute.
- **Impact**: Concurrent workflow approvals cause silent overwrite. Status transitions lost.
- **Fix**: Add `.eq(StlSettlement::getSettlementStatus, "DRAFT")` guard to the update. Or add `@Version` to `StlSettlement` entity.
- **Dependencies**: Same module as S-P0-03. Should be done after settlement service TOCTOU fix.

#### S-P1-14 | D5-004 | taxAmount/amountWithoutTax not populated in 3 of 4 cost strategies

- **Files**: `MaterialReceiptCostStrategy.java`, `SubMeasureCostStrategy.java`, `VarOrderCostStrategy.java`
- **Issue**: Only `ContractCostStrategy` populates `taxAmount` and `amountWithoutTax` on generated `CostItem`. The other 3 strategies set only `amount`, leaving tax fields as DB default 0. If source entities carry tax data, it is silently lost.
- **Impact**: Cost reports show zero tax breakdown for material receipts, subcontract measures, and variation orders. Financial reporting incomplete.
- **Fix**: Populate tax fields from source entities if available, or document intentional omission with explicit `setTaxAmount(BigDecimal.ZERO)`.
- **Dependencies**: None (touches 3 strategy files, independent of all other changes).

---

## 5. P2 Catalogue (Backlog — No Fix Tasks Created)

### Security & Configuration (7 items)

| ID | Source | File | Issue |
|----|--------|------|-------|
| P2-01 | D1-005 | `CookieUtils.java:23` | Secure flag defaults to false |
| P2-02 | D1-006 | `SecurityConfig.java:45-46` | Missing security headers in Spring Security |
| P2-03 | D1-007 | `logback-spring.xml:7,25,40` | Log masking regex misses JSON/structured formats |
| P2-04 | D1-008 | `GlobalExceptionHandler.java:73-86` | Exception messages may leak sensitive data |
| P2-05 | D1-011 | `nginx.conf:36-80,85-164` | Missing CSP; HTTP block lacks all security headers |
| P2-06 | D1-012 | `Dockerfile:83`, `docker-compose.prod.yml:114` | Missing container security constraints (read_only, cap_drop) |
| P2-07 | D1-013 | `FileService.java:42-46` | Extension whitelist incomplete; no MIME validation |

### Backend Code (5 items)

| ID | Source | File | Issue |
|----|--------|------|-------|
| P2-08 | D2-005 | 4 files | Service classes > 500 lines (StlSettlement 590, CostSummary 572, Alert 567, Dashboard 532) |
| P2-09 | D2-006 | `WorkflowCoreService.java:165-196` | Near-duplicate cancel methods |
| P2-10 | D2-007 | `AlertEvaluationService.java:107`, `CostSummaryService.java:408` | Self-invocation bypasses @Transactional |
| P2-11 | D2-008 | `VarOrderCostStrategy.java:81` | Inconsistent costSubjectId resolution (no null-fallback to resolver) |
| P2-12 | D2-009 | `ProfileController.java:34` | `updateProfile()` missing `@Valid` (inconsistency with `changePassword()`) |

### Frontend (4 items)

| ID | Source | File | Issue |
|----|--------|------|-------|
| P2-13 | D3-006 | `invoice/index.vue:59,352-379` | `:any` type assertions (6 instances) |
| P2-14 | D3-007 | `stores/` (3 files) | DEV-guarded console.error in production bundle (12 instances) |
| P2-15 | D3-008 | `components/` (2 files) | DEV-guarded console.error in components (10 instances) |
| P2-16 | D3-009 | 25 files | 50 ESLint errors (28 unused-vars, 10 no-explicit-any, 7 vue/no-unused-vars) |

### Data & Infrastructure (7 items)

| ID | Source | File | Issue |
|----|--------|------|-------|
| P2-17 | D4-001 | `V47__add_user_preference.sql:27` | `deleted_flag` SMALLINT vs project convention TINYINT |
| P2-18 | D4-002 | `V47__add_user_preference.sql:26` (H2) | Unnamed UNIQUE constraint in H2 (named in MySQL) |
| P2-19 | D4-004 | V22–V44 migrations | `created_at` vs `created_time` naming split (fixed in V45) |
| P2-20 | D4-006 | All V*.sql | No DB-level FOREIGN KEY constraints (application-level only) |
| P2-21 | D4-008 | `frontend-admin/Dockerfile:18` | `COPY public/` commented out |
| P2-22 | D4-012 | `.github/workflows/ci.yml:258-259` | Pre-deploy DB backup commented out |
| P2-23 | D4-014 | `.github/workflows/ci.yml:183,214` | `docker-build` skips on workflow_dispatch |

### Business Logic (2 items — verified correct, document as known-safe)

| ID | Source | Area | Status |
|----|--------|------|--------|
| P2-24 | D5-005–D5-010 | Workflow @Version, cost idempotency, warranty calc | **All verified correct** |
| P2-25 | D5-005–D5-010 | MatStock retry, isCritical() intact, SSE proxy_buffering | **All verified correct** |

---

## 6. P3 Catalogue (Cosmetic/Suggestions — No Fix Tasks)

| Count | Source Dimension | Representative Items |
|-------|-----------------|---------------------|
| 8 | D1 Security | MINIO_ROOT_USER default "admin", XSS disabled without CSP, Dockerfile timezone hardcoded, nginx gzip_types missing fonts, actuator info leak, etc. |
| 5 | D2 Backend | 30+ controllers bind Entity directly (mitigated), @SuppressWarnings justified, misleading variable name, AOP self-invocation fragility, manual VO field copy |
| 5 | D3 Frontend | Token refresh catch edge case, route guard verified OK, route-to-component all correct, KPI endpoint verified, 401 queue pattern edge case |
| 6 | D4 Infra | V50/V51 inconsistent DROP syntax, missing created_at indexes on many tables, CI pnpm version mismatch, deprecated docker-compose version field, dev compose no resource limits, CI flyway-check timeout |
| 2 | D5 Business | SSE proxy_buffering verified correct both HTTP/HTTPS, PURCHASE_REQUEST switch case present |

---

## 7. Dependency Graph

```
┌─────────────────────────────────────────────────────────────────┐
│ P0 — Settlement TOCTOU (S-P0-03) ← Flyway V52 migration needed  │
│   ├── S-P1-12 (code sequence race, same method) → merge in T8   │
│   └── S-P1-13 (finalization lock, same module) → T19 after T8   │
│                                                                  │
│ P0 — Frontend system/data bypass (S-P0-01) [INDEPENDENT]        │
│ P0 — Frontend profile bypass (S-P0-02) [INDEPENDENT]            │
│                                                                  │
│ P1 — deploy/.env security (S-P1-01 + S-P1-11) [INDEPENDENT]     │
│ P1 — Dockerfile hardening (S-P1-02 + S-P1-10) [INDEPENDENT]     │
│ P1 — Nginx security headers (S-P1-03) [INDEPENDENT]              │
│ P1 — Auth rate limiting (S-P1-04) [INDEPENDENT, needs lib]      │
│ P1 — Invoice empty catch (S-P1-06) [INDEPENDENT, 1-line fix]    │
│ P1 — Cost tax fields (S-P1-14) [INDEPENDENT]                     │
│                                                                  │
│ P1 — N+1 fix (S-P1-05) ─── conflicts with ─── S-P1-07 (readOnly)│
│   → S-P1-07 must go AFTER S-P1-05 (same file touched)           │
│                                                                  │
│ P1 — Frontend catch blocks (S-P1-08) [37 files, HUGE blast]     │
│   → Must go AFTER P0 frontend fixes (T9, T10)                    │
│ P1 — Frontend catch any (S-P1-09) [2 files, small blast]        │
│   → Can run parallel with S-P1-08 if same files avoided          │
└─────────────────────────────────────────────────────────────────┘
```

### Blocking Dependencies

| Blocker | Blocks | Reason |
|---------|--------|--------|
| Flyway V52 migration | T8 (settlement TOCTOU) | Must exist before service code can insert |
| T8 (settlement races fixed) | T19 (settlement finalization lock) | Same module, need context of post-fix code |
| T9, T10 (P0 frontend fixes) | T21 (122 catch blocks) | S-P1-08 touches 37 files including data + profile pages |
| T17 (N+1 fix) | T18 (readOnly) | Both touch PayApplicationService.java |

### Independent (Maximum Parallelism)

T9, T10, T11, T12, T13, T14, T15, T16, T17, T20 can all run in parallel (different files, different layers).

---

## 8. Fix Wave Structure

### Wave 3-A: P0 Fixes (3 tasks, can all run in parallel)

| Task | Synthesis ID | Description | Layer | Dependencies |
|------|-------------|-------------|-------|--------------|
| **T8** | S-P0-03 + S-P1-12 | Fix settlement TOCTOU races: Flyway V52 UNIQUE constraint + DuplicateKeyException handling + retry loop for code generation | Backend | Requires Flyway migration |
| **T9** | S-P0-01 | Fix system/data API module bypass: create `clearDatabase()` in `api/modules/system.ts` | Frontend | None |
| **T10** | S-P0-02 | Fix profile API module bypass: create `updateProfile()` / `changePassword()` in `api/modules/user.ts` | Frontend | None |

### Wave 3-B: P1 Independent Fixes (6 tasks, all parallel)

| Task | Synthesis ID | Description | Layer | Dependencies |
|------|-------------|-------------|-------|--------------|
| **T11** | S-P1-01 + S-P1-11 | Fix deploy/.env security: `.env.example` placeholders → real defaults + `.env` security documentation | Infra | None |
| **T12** | S-P1-02 + S-P1-10 | Fix backend Dockerfile hardening: useSSL env variable + sentinel ENV defaults + startup validation | Infra | None |
| **T13** | S-P1-03 | Fix nginx security: uncomment HSTS + HTTP→HTTPS redirect + add Content-Security-Policy header | Infra | None |
| **T14** | S-P1-04 | Add rate limiting to /auth/login and /auth/refresh (Redis-based counter) | Backend | Needs lib decision |
| **T15** | S-P1-06 | Fix empty catch block in InvoiceService.java:210 (add log.debug) | Backend | None |
| **T16** | S-P1-14 | Populate taxAmount/amountWithoutTax in MaterialReceipt, SubMeasure, VarOrder cost strategies | Backend | None |

### Wave 3-C: P1 Sequenced Fixes (3 tasks, partially parallel)

| Task | Synthesis ID | Description | Layer | Dependencies |
|------|-------------|-------------|-------|--------------|
| **T17** | S-P1-05 | Fix N+1 queries in PayApplicationService.getById() — refactor to batch pre-fetch | Backend | None (but blocks T18) |
| **T18** | S-P1-07 | Add @Transactional(readOnly=true) to all read-only service methods (42+ files) | Backend | T17 (same files) |
| **T19** | S-P1-13 | Fix settlement finalization optimistic lock — add status guard to SettlementWorkflowHandler.onApproved() | Backend | T8 (same module) |

### Wave 3-D: P1 Frontend Sequenced Fixes (2 tasks, sequential)

| Task | Synthesis ID | Description | Layer | Dependencies |
|------|-------------|-------------|-------|--------------|
| **T20** | S-P1-09 | Replace catch (e: any) with catch (e: unknown) in invoice/index.vue + system/users/index.vue | Frontend | T9, T10 (avoid conflict) |
| **T21** | S-P1-08 | Fix 122 no-param catch blocks across 37 files — add (e: unknown) + DEV logging | Frontend | T9, T10, T20 (largest blast radius, goes last) |

### Wave 4: Gate Validation (after all Wave 3)

| Task | Description |
|------|-------------|
| F1 | Backend regression: `./mvnw test` ≥174 pass |
| F2 | Frontend build: `pnpm build` zero TS errors + `pnpm lint` zero errors |
| F3 | E2E smoke: Playwright 9 specs pass |
| F4 | Dimension rescan: verify top-3 P0/P1 fixes |

---

## 9. Grade Justifications (Borderline Cases)

### Upgraded
- **D1-009 (P2→P1 in S-P1-10)**: D1 reviewer graded P2 but description states "JWT signing with an empty key is a critical vulnerability." This meets P1 criteria (should fix before launch). Merged with D4-007 which independently graded P1. **Justification**: Empty JWT_SECRET in standalone Docker run is a P1 deployment security issue.

### Downgraded
- **D1-010 (P2→P1 in S-P1-02)**: D1-010 itself is P2 (breadth of useSSL=false across all configs), but its parent D1-002 was independently graded P1. The merged finding inherits the higher grade. **Justification**: The docker-compose.prod.yml override is the primary concern (P1).

### Confirmed P0
- **D3-001, D3-002 (P0)**: Confirmed. API module bypass breaks architectural contract and creates untyped, unmaintainable code paths. If the interceptor or request wrapper changes, these pages silently break. P0 is correct per "blocks production deployment" criteria.
- **D5-001 (P0)**: Confirmed. Data corruption (duplicate settlements) is definitively a production blocker.

### Regression (D6) P0 Count
- **0 P0, 0 regressions**: All 10/10 verification checks pass. Audit-fixes (24/24 tasks) remain stable at HEAD. No findings to synthesize.

---

## 10. Verification Strategy

### Synthesis Self-Check

| Check | Status |
|-------|--------|
| All 6 review reports read completely | YES |
| Cross-dimension dedup applied (6 merges) | YES |
| Every P0/P1 finding has a fix task (T8–T21) | YES |
| No fix tasks created for P2/P3 (catalogue only) | YES |
| Dependency graph documents all blocking relationships | YES |
| Fix wave structure enables maximum parallelism | YES |
| Grade justifications provided for all borderline cases | YES |
| Original review reports unmodified | YES |

---

## Appendix A: Source Report Inventory

| File | Dimension | Findings | P0 | P1 | P2 | P3 |
|------|-----------|----------|----|----|----|----|
| `review-1-security-config.md` | D1 Security & Config | 15 (+6 appendix) | 0 | 4 | 8 | 9 |
| `review-2-code-architecture.md` | D2 Backend Code | 14 | 0 | 4 | 5 | 5 |
| `review-3-frontend-api.md` | D3 Frontend & API | 14 | 2 | 3 | 4 | 5 |
| `review-4-data-infra.md` | D4 Data & Infra | 15 | 0 | 2 | 6 | 7 |
| `review-5-business-logic.md` | D5 Business Logic | 12 (4 issues + 8 verified) | 1 | 3 | 8 | 0 |
| `review-6-fix-regression.md` | D6 Regression | 0 | 0 | 0 | 0 | 0 |
| **Total (raw)** | | **73** | **3** | **16** | **31** | **26** |
| **Consolidated** | | **67** | **3** | **14** | **25** | **28** |

## Appendix B: Complete Fix Task Index

| Task | Synthesis IDs | P-Level | Layer | Wave | Description |
|------|--------------|---------|-------|------|-------------|
| T8 | S-P0-03, S-P1-12 | P0 | Backend | 3-A | Settlement TOCTOU races (Flyway V52 + service fix) |
| T9 | S-P0-01 | P0 | Frontend | 3-A | system/data API module bypass |
| T10 | S-P0-02 | P0 | Frontend | 3-A | profile API module bypass |
| T11 | S-P1-01, S-P1-11 | P1 | Infra | 3-B | deploy/.env security (.example + .env) |
| T12 | S-P1-02, S-P1-10 | P1 | Infra | 3-B | Dockerfile hardening (useSSL + empty ENV) |
| T13 | S-P1-03 | P1 | Infra | 3-B | Nginx security headers (HSTS + CSP + redirect) |
| T14 | S-P1-04 | P1 | Backend | 3-B | Auth rate limiting |
| T15 | S-P1-06 | P1 | Backend | 3-B | InvoiceService empty catch fix |
| T16 | S-P1-14 | P1 | Backend | 3-B | Cost strategy tax fields |
| T17 | S-P1-05 | P1 | Backend | 3-C | PayApplication N+1 fix |
| T18 | S-P1-07 | P1 | Backend | 3-C | @Transactional(readOnly=true) (42+ services) |
| T19 | S-P1-13 | P1 | Backend | 3-C | Settlement finalization lock |
| T20 | S-P1-09 | P1 | Frontend | 3-D | catch (e: any) → unknown |
| T21 | S-P1-08 | P1 | Frontend | 3-D | 122 no-param catch blocks |

---

*Synthesis complete. Fix tasks T8–T21 appended to `.sisyphus/plans/consolidated-remaining-work.md`.*
