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
- 5 `record.approvalStatus as any` patterns in older Vue pages (variation, purchase, subcontract, payment, receipt)`n
