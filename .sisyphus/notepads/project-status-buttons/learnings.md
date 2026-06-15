# Learnings — project-status-buttons

## 2026-06-15

### Changes Applied
- Added `STATUS_LABEL` constant mapping English status keys to Chinese labels (DRAFT→前期, ONGOING→在建, etc.)
- Replaced `STATUS_COLOR` keys from Chinese to English (DRAFT→processing, ONGOING→success, etc.)
- Updated filter dropdown from 4 Chinese-value options to 5 English-value + Chinese-label options (added CLOSED)
- Updated status column template to display Chinese labels via `STATUS_LABEL[record.status]`
- Added `useRouter` import and router instance for programmatic navigation
- Replaced placeholder view/edit buttons with `router.push()` to `/project/:id/overview` and `/project/:id/edit`

### Verification
- `vue-tsc --noEmit`: passed (zero TypeScript errors)
- `vite build`: failed due to pre-existing missing file `src/pages/project/edit.vue` (unrelated to these changes)

### Notes
- APPROVAL_COLOR was NOT modified per task constraints
- TYPE_COLOR was NOT modified
- The build failure is a pre-existing routing gap — `edit.vue` is imported by router but not yet created
- Status filter now sends English values to backend (DRAFT/ONGOING/COMPLETED/SUSPENDED/CLOSED), matching the backend project_status dictionary

### Task: Create edit.vue
- Created `frontend-admin/src/pages/project/edit.vue` — project edit page
- Route already registered: `:projectId/edit` → `ProjectEdit` → `edit.vue` (router line 159-163)
- Route param is `projectId` (not `id`), consistent with overview.vue and members.vue
- API: `getProjectDetail(id)` returns `ProjectVO`, `updateProject(id, data)` returns `void`
- Contract amount: API stores as string in yuan (e.g. "580000000.00"), form displays in 万元 (÷10000), saves back by ×10000 → String()
- Form follows same field structure as index.vue create modal (9 fields)
- Breadcrumb pattern from overview.vue: `<a-breadcrumb>` + `<a-page-header>`
- Loading pattern from overview.vue: `<a-spin :spinning="loading">`
- Styles reuse `.pj-page`, `.pj-card`, `.pj-header` from index.vue
- `vue-tsc --noEmit`: zero errors
- `pnpm build`: zero errors — resolves the pre-existing build failure caused by missing edit.vue

### F4 Scope Fidelity Audit (2026-06-15)

**Commits reviewed**: `HEAD~3..HEAD` (3 commits)
```
520db3bb feat(project): add project edit page
de53e820 feat(project): add status Chinese mapping and view/edit button navigation
e05eac1e fix(project): set default status DRAFT on project creation
```

**Files changed**: 4 (all in plan)
- `backend/.../PmProjectService.java` — Task 1 ✅
- `frontend-admin/src/pages/project/index.vue` — Task 2 ✅
- `frontend-admin/src/router/index.ts` — Task 3 ✅
- `frontend-admin/src/pages/project/edit.vue` — Task 4 ✅

**Per-task findings**:
- Task 1: +1 line `project.setStatus("DRAFT")` at exact location. Clean.
- Task 2: 6 changes exactly as specified. APPROVAL_COLOR/TYPE_COLOR/createModal untouched. Clean.
- Task 3: 1 route added. No other routes modified. Clean.
- Task 4: 245-line new file. All 7 spec requirements met. No member/status/approvalStatus code. Clean.

**Guardrails verified**: All 6 Must-NOT-Have rules compliant.
**Contamination**: None. All changes confined to specified files.
**Unaccounted changes**: None. Every diff line maps to a spec requirement.
**VERDICT: APPROVE**
