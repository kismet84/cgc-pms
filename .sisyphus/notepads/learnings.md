## Task 19: Alert Center Frontend (2026-06-12)

### Patterns used
- List+Filter paradigm: followed pages/project/index.vue pattern - filter card, a-table, separate a-pagination
- Pinia store: followed stores/contract.ts pattern - setup store with refs, async actions, resetState
- API module: followed api/modules/workflow.ts pattern - typed request functions
- CSS naming: al- prefix (matching pj- for project, wf- for workflow)

### Key decisions
- Backend GET /alerts returns flat list (not paginated), so client-side pagination via computed pagedAlerts
- Project dropdown populated by existing getProjectList API (pageSize=200)
- Severity tags: SEVERITY_COLOR map (HIGH=red, MEDIUM=orange, LOW=default)
- isRead toggle: mark-read button only for unread alerts, per-row loading via markingRead Set
- batch-evaluate button in page-header extra slot with danger type

### Files created
- src/types/alert.ts: AlertLogVO + RULE_TYPE_LABELS + SEVERITY_COLOR
- src/api/modules/alert.ts: getAlertList, markAlertRead, batchEvaluate
- src/stores/alert.ts: useAlertStore
- src/pages/alert/index.vue: full list page
- src/router/index.ts: added /alert route

## Task 17: Contract Changes Tab (2026-06-12)

### Patterns used
- Tab integration: Followed existing 3-tab pattern in ContractDetailPage.vue - added 4th `<a-tab-pane key="contract-changes">`
- List+Form component pattern: Single component (ContractChangeList.vue) with embedded modal for create/edit - matches variation/order.vue pattern
- API module: Followed api/modules/variation.ts pattern - typed request functions, CRUD + submit
- Amount impact display: before→change→after flow with color-coded amounts (gray→red/blue→green)
- CSS: cc- prefix for contract-change specific styles, consistent with existing pm-/ct-/wf-/al- prefixes

### Key decisions
- Change types: AMOUNT (金额变更), DURATION (工期变更), CLAUSE (条款变更) - with COLOR map
- Approval timeline: Simplified Steps component showing DRAFT→APPROVING→APPROVED/REJECTED progression (workflow instance details fetched by backend internally)
- Edit guard: costGeneratedFlag=1 disables edit/delete, approvalStatus!=DRAFT disables edit/delete
- Submit approval: Modal.confirm pattern matching existing contract submit flow
- AfterAmount auto-calculated: beforeAmount + changeAmount on the frontend for preview
- ContractChangeList is a standalone component receiving contractId prop - self-contained data fetching

### Backend alignment
- Backend uses Long IDs (Snowflake), frontend types use string
- Backend entity fields: changeType, beforeAmount, changeAmount, afterAmount, reason, approvalStatus, effectiveFlag, costGeneratedFlag
- API: GET/POST/PUT/DELETE /contract-changes, POST /contract-changes/{id}/submit
- Response format: ApiResponse wraps data, request.ts extracts .data
- Page params: pageNo/pageSize (not pageNum/pageSize)

### Pre-existing build issues resolved
- cost-target/index.vue: `<template #icon>` inside `<a>` tag caused Vue compiler error - replaced with direct `<CheckCircleOutlined>` component
- dashboard/index.vue: Missing placeholder file - created minimal stub (used by Dashboard, ContractApproval, System routes)

### Files created
- src/types/contract-change.ts: ContractChangeVO + ContractChangeQueryParams + label/color maps
- src/api/modules/contract-change.ts: 6 API functions (list, detail, create, update, delete, submit)
- src/components/ContractChangeList.vue: List table + create/edit/detail modals + approval steps timeline

### Files modified
- src/pages/contract/ContractDetailPage.vue: Added import + 4th tab pane
- src/pages/cost-target/index.vue: Fixed template icon issue (pre-existing)
- src/pages/dashboard/index.vue: Created placeholder (was missing)

## Task 23: Vite 5→6 Upgrade / esbuild Vulnerability Fix (2026-06-12)

### Status
- **Already done** — package.json already had vite ^6.4.3 and @vitejs/plugin-vue ^6.0.7
- esbuild@0.25.0 (bundled with vite 6.4.3) — no known vulnerabilities

### Verification results
- pnpm dev: VITE v6.4.3, ready in 514ms, port 5173 ✓
- pnpm build: exit 0, 4421 modules transformed, 11.90s ✓
- pnpm audit (official registry): No known vulnerabilities found ✓
- vite.config.ts: No breaking changes needed — config is compatible with vite 6

### Notes
- The npmmirror.com registry lacks audit endpoint — use official npm registry for pnpm audit

## F2: Code Quality Review — Phase 3 (2026-06-12)

### Build Results
- Backend: `mvnw compile` PASS (no errors)
- Frontend: `pnpm build` PASS (vue-tsc + vite, 4421 modules, 25.02s)

### Scope
55 files across 5 commits: 25 backend Java + 27 frontend TS/Vue + 3 SQL migrations

### Key Findings
- **Tenant filtering**: ALL new queries properly include tenantId. No gaps found.
- **`@ts-ignore`**: 0 occurrences — clean.
- **`console.log`**: 0 in production code.
- **Empty catch**: 0 in production (1 intentional `NumberFormatException ignored` in CtContractChangeService).
- **`as any`**: 2 new instances (settlement/index.vue:96, cost-target/edit.vue:105) + 5 pre-existing.
- **Unused imports**: 1 (PageResult in alert/index.vue:6).
- **AI slop**: Widespread section-header over-commenting (~90 dividers across 7 Vue components) + ~35 redundant JSDoc on trivial API wrappers.

### Clean Files
- All backend handlers/services/controllers: no functional issues
- All SQL migrations: clean, INSERT IGNORE for idempotency
- All type definition files: clean, comprehensive
- Pinia stores: functional but thin (over-abstraction in costTarget/settlement)

### Recurring Pattern
Section-header comments like `// ---- Filter state ----`, `// ── Fetch ──` appear across all new Vue pages. This is a consistent AI-generation signature. Function names and code structure already provide this partitioning; the comments add noise without value.

### Pre-existing Issues (not Phase 3)
- `api/modules/contract.ts:50-54`: Commented-out code block with TODO
- 5 `record.approvalStatus as any` patterns in older Vue pages (variation, purchase, subcontract, payment, receipt)

## Task 10: orgId Backfill Service (2026-06-12)

### Patterns used
- MyBatis-Plus LambdaUpdateWrapper with `.isNull()` for targeting only NULL orgId rows
- MyBatis-Plus LambdaQueryWrapper with `.select()` to fetch only tenantId column (efficiency)
- @Transactional on the public entry method — wraps entire multi-tenant backfill
- Idempotent design: check-before-create for org entities, isNull condition for updates

### Key decisions
- Service operates directly on mappers (not through existing OrgCompanyService/OrgDepartmentService) because those services check UserContext which is request-scoped — backfill needs to work across ALL tenants
- Discovered 2 tenants needing backfill in test run: tenant 0 (seed data with null orgId) + tenant 999 (test data)
- ROOT company code = "ROOT", ROOT department code = "ROOT_DEPT" — simple constants
- Backfill uses `pmProjectMapper.update(null, wrapper)` — passing null entity with LambdaUpdateWrapper sets specific columns

### Files created
- backend/src/main/java/com/cgcpms/org/service/OrgInitService.java: Multi-tenant backfill service
- backend/src/test/java/com/cgcpms/org/OrgInitServiceTest.java: 3 test cases (full backfill, idempotency, no-op)

### Test results
- OrgInitServiceTest: 3/3 PASSED (TC1: full backfill, TC2: idempotency, TC3: no-op when all set)
- OrgServiceTest: 18/18 PASSED (no regression)
- Full suite: 108 tests, 99 pass, 7 pre-existing failures (MatStockServiceTest), 2 pre-existing errors (WorkflowEngineIntegrationTest) — all unrelated to this task

### Gotchas
- Surefire ClassNotFoundException on first run — test class was compiled but forked JVM couldn't find it; resolved by re-running (likely a timing/surefire issue)
- JDK path in README (D:\projects-test\jdk-21) doesn't exist directly — actual JDK is at D:\projects-test\jdk-21\jdk-21.0.11+10
- `grep` tool's `|` character gets interpreted by PowerShell — avoid pipe in regex patterns on Windows`n

## Task 21: Alert -> Notification Wiring (2026-06-12)

### Patterns used
- @Scheduled-safe: NotificationService.create() takes explicit tenantId/userId, never reads UserContext
- PmProjectMember lookup: PM role preferred, first active fallback
- Notification failure is non-fatal: try-catch in alert insert loop, logged at WARN level
- MyBatis-Plus entity design: SysNotification does NOT extend BaseEntity (minimalist columns)

### Key decisions
- tenantId from project record (project.getTenantId()), NOT UserContext - essential for @Scheduled threads
- userId from pm_project_member query (role_code='PM' preferred)
- BizType='ALERT' for alert-triggered notifications
- Notification title mapping: ruleType -> Chinese name via switch expression
- Silent skip when no project member exists (log at DEBUG, no exception)

### Gotchas
- PmProjectMember uses created_time/updated_time (not BaseEntity's created_at/updated_at) - must set explicit fields
- Pre-existing compilation errors in WorkflowEngine callers (stale .class files) - fixed by clean compile
- mvnw.cmd on PowerShell must be called from backend/ directory

## Task 25: Project Overview Page (2026-06-12)

### Patterns used
- KPI cards: Exact pattern from dashboard page — `.kpi-grid > .kpi-card > .kpi-icon + .kpi-body`
- ECharts: `v-chart` with `pie` series following dashboard's `costBarOption`/`costLineOption` patterns
- API module: Followed `api/modules/project.ts` existing pattern — typed `request<T>()` functions
- Router: Converted flat `/project` route to parent+children pattern (matching contract/cost/settlement)
- Amount formatting: `toLocaleString('zh-CN')` with `fmtWan` helper (divide by 10000)
- CSS: Reused dashboard scoped styles exactly (kpi-card, panel, chart-row, empty-hint)

### Key decisions
- All amounts displayed from backend `ProjectOverviewVO` — no frontend calculation
- Route: `/project/:projectId/overview` as child of `/project` parent
- Parent route now redirects `/project` → `/project/list` (backward compat)
- Pie chart: donut style (radius ['45%', '72%']) with paid/unpaid/dynamic cost breakdown
- Members table: role codes mapped to human-readable labels via `roleLabels` map
- Summary row: additional quick-glance metrics (contract count, member count, monthly warnings, unpaid amount)

### Files changed
- `src/types/project.ts`: Added `ProjectOverviewVO` + `MemberBriefVO`
- `src/api/modules/project.ts`: Added `getProjectOverview(projectId)`
- `src/pages/project/overview.vue`: **New** — full overview page
- `src/router/index.ts`: Converted project route to parent+children

### Verification
- `pnpm type-check` (vue-tsc --noEmit): PASS, zero errors
- LSP diagnostics: clean on all 4 changed files
