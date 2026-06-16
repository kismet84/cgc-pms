# task-014-project-target-ui-redesign

## Title

Redesign Project Management and Target Management pages using the approved new UI language.

## Status

Assigned to development.

## User Request

User asked to execute `task-014` after approving the full UI page code template approach. This task covers the first rollout batch from the template guide: `项目管理` and `目标管理`.

## Goal

Update the project and target management pages so they match the approved `清爽企业级工作台` UI language already applied to the dashboard and contract ledger.

The implementation should improve visual consistency, density, filtering, KPI rhythm, table readability, and responsive behavior without changing backend behavior.

## Design Inputs

- `D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-13-new-ui-redesign-design.md`
- `D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-16-ui-page-code-templates.md`
- Dashboard reference:
  - `D:\projects-test\cgc-pms\.agent-runtime\reports\task-011-screenshots\dashboard-desktop-1440x900-verified.png`
- Contract ledger reference:
  - `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-contract-ledger-v2.png`

## Target Routes And Files

Project Management routes:

- `/project/list`
- `/project/:projectId/overview`
- `/project/:projectId/members`
- `/project/:projectId/edit`

Project files:

- `D:\projects-test\cgc-pms\frontend-admin\src\pages\project\index.vue`
- `D:\projects-test\cgc-pms\frontend-admin\src\pages\project\overview.vue`
- `D:\projects-test\cgc-pms\frontend-admin\src\pages\project\members.vue`
- `D:\projects-test\cgc-pms\frontend-admin\src\pages\project\edit.vue`
- `D:\projects-test\cgc-pms\frontend-admin\src\api\modules\project.ts`
- `D:\projects-test\cgc-pms\frontend-admin\src\types\project.ts`

Target Management routes:

- `/cost-target/index`
- `/cost-target/create`
- `/cost-target/:id/edit`

Target files:

- `D:\projects-test\cgc-pms\frontend-admin\src\pages\cost-target\index.vue`
- `D:\projects-test\cgc-pms\frontend-admin\src\pages\cost-target\edit.vue`
- `D:\projects-test\cgc-pms\frontend-admin\src\api\modules\costTarget.ts`
- `D:\projects-test\cgc-pms\frontend-admin\src\types\costTarget.ts`

Allowed focused tests:

- `D:\projects-test\cgc-pms\frontend-admin\src\pages\project\__tests__\ProjectTargetUiRedesign.test.ts`
- Existing `ProjectNav.test.ts` may be updated only if needed for new source markers or preserved navigation behavior.

## Scope

Allowed:

- Redesign the listed project and target pages.
- Add local presentation-only fallback statistics only where existing APIs do not provide enough data for visual density.
- Add scoped CSS in the page files or small shared classes in `frontend-admin/src/assets/styles/global.css` if reused by both modules.
- Add focused source-level tests for required UI markers and route/page labels.

Not allowed:

- Backend changes.
- API contract changes.
- Route changes beyond visual labels already approved.
- Refactoring unrelated modules.
- Reverting unrelated user changes.
- Touching `.sisyphus/boulder.json`.

## Required UI Direction

Use the template guide patterns:

- Project list: `Ledger List Page`.
- Project overview: `Analysis Dashboard Page` plus `Detail Page` summary patterns.
- Project members: `Tree And Table Management Page` or compact list management pattern.
- Project edit: `Create Or Edit Form Page`.
- Target list: `Tree And Table Management Page` plus ledger/KPI rhythm.
- Target edit: `Create Or Edit Form Page`.

All redesigned pages should use:

- compact `app-page` structure;
- white panels with subtle border;
- compact filter surface;
- KPI strip when useful;
- dense table headers and row heights;
- semantic status tags;
- no nested cards;
- no marketing hero sections;
- no dark/purple/beige decorative palette;
- mobile-safe layout with no shell-level horizontal overflow.

## Page Requirements

### Project List `/project/list`

Target structure:

1. Page header:
   - breadcrumb: `项目管理 / 项目列表`;
   - title: `项目列表`;
   - primary action: existing create/edit action if available.
2. Filter surface:
   - project name / code keyword;
   - project status;
   - project manager;
   - date range if existing fields support it.
3. KPI strip:
   - `项目总数`;
   - `在建项目`;
   - `已完工项目`;
   - `风险项目` or `暂停项目`.
4. Main table:
   - keep existing data fields and actions;
   - use compact table treatment;
   - status tags should be restrained and consistent.
5. Optional right analysis rail if page width supports it:
   - `项目状态分布`;
   - `项目风险提示`;
   - `近期项目`.

### Project Overview `/project/:projectId/overview`

Target structure:

1. Page header:
   - breadcrumb: `项目管理 / 项目总览`;
   - title from project name when available.
2. Summary strip:
   - project status;
   - project manager;
   - contract amount;
   - start/end dates or progress.
3. Analysis panels:
   - `项目经营概览`;
   - `成本执行概览`;
   - `关键风险`.
4. Bottom panels:
   - `合同清单`;
   - `待办事项`;
   - `项目成员`.

### Project Members `/project/:projectId/members`

Target structure:

1. Page header:
   - breadcrumb: `项目管理 / 项目成员`;
   - title: `项目成员`;
   - primary action for add member if existing.
2. KPI strip:
   - `成员总数`;
   - `项目经理`;
   - `业务人员`;
   - `待确认`.
3. Main table:
   - member name;
   - role;
   - department;
   - phone/email if existing;
   - status;
   - operation.
4. Optional side panel:
   - role distribution;
   - member responsibility tips.

### Project Edit `/project/:projectId/edit`

Target structure:

1. Page header:
   - breadcrumb: `项目管理 / 编辑项目`;
   - title: `编辑项目` or `新建项目`;
   - submit/cancel actions.
2. Form panels:
   - `基础信息`;
   - `项目周期`;
   - `金额信息`;
   - `备注与附件` if existing.
3. Form layout:
   - two-column on desktop;
   - single-column on mobile;
   - bottom or top action area remains easy to reach.

### Target List `/cost-target/index`

Target structure:

1. Page header:
   - breadcrumb: `目标管理 / 目标管理`;
   - title: `目标管理`.
2. Filter surface:
   - project;
   - target status;
   - keyword;
   - date range if existing.
3. KPI strip:
   - `目标总额`;
   - `已锁定成本`;
   - `动态成本`;
   - `偏差金额`.
4. Main layout:
   - target/cost subject table or list;
   - optional left subject tree if existing data supports it;
   - analysis rail:
     - `目标占比`;
     - `偏差预警`;
     - `审批状态`.

### Target Edit `/cost-target/create`, `/cost-target/:id/edit`

Target structure:

1. Page header:
   - breadcrumb: `目标管理 / 新建目标成本` or `目标管理 / 编辑目标成本`;
   - title from mode.
2. Form panels:
   - `基础信息`;
   - `成本科目`;
   - `金额明细`;
   - `审批与备注`.
3. Keep existing submit/save behavior.

## Testing Requirements

Follow TDD where practical:

1. Add a focused source-level test before implementation.
2. Confirm the test fails because new markers are not present.
3. Implement UI changes.
4. Confirm the test passes.

Minimum assertions:

- Project pages contain these labels:
  - `项目列表`;
  - `项目总览`;
  - `项目成员`;
  - `项目状态分布`;
  - `项目经营概览`.
- Target pages contain these labels:
  - `目标管理`;
  - `目标总额`;
  - `已锁定成本`;
  - `动态成本`;
  - `偏差预警`.
- Shared/new UI source markers are present:
  - `project-target-redesign`;
  - `pt-kpi-strip`;
  - `pt-filter-surface`;
  - `pt-analysis-rail`;
  - `pt-panel`.
- Old label `目标成本管理` must not be reintroduced.

Suggested test command:

```powershell
cd frontend-admin
pnpm vitest run src/pages/project/__tests__/ProjectTargetUiRedesign.test.ts --configLoader runner
```

## Verification Commands

Run at minimum:

```powershell
cd frontend-admin
pnpm vitest run src/pages/project/__tests__/ProjectTargetUiRedesign.test.ts --configLoader runner
pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts src/layouts/__tests__/BasicLayout.mobile.test.ts src/pages/project/__tests__/ProjectTargetUiRedesign.test.ts src/pages/dashboard/__tests__/DashboardReferenceFidelity.test.ts src/pages/dashboard/__tests__/DashboardRoleUnification.test.ts src/pages/contract/__tests__/ContractLedgerReferenceFidelity.test.ts --configLoader runner
pnpm build
```

If existing `ProjectNav.test.ts` remains relevant, include it:

```powershell
pnpm vitest run src/pages/project/__tests__/ProjectNav.test.ts src/pages/project/__tests__/ProjectTargetUiRedesign.test.ts --configLoader runner
```

## Browser Verification

Use the in-app browser or Playwright against the local app.

Verify:

- `/project/list`;
- `/project/:projectId/overview` using an existing or mocked project ID;
- `/project/:projectId/members` using an existing or mocked project ID;
- `/project/:projectId/edit` using an existing or mocked project ID;
- `/cost-target/index`;
- `/cost-target/create`.

Viewports:

- `1440x900`;
- around `937x900`;
- `390x844`.

Required checks:

- no blank page;
- no framework error overlay;
- no relevant console errors;
- no shell-level horizontal overflow;
- title, filters, KPI strip, main table/form panels visible as applicable;
- project/target routes still preserve existing primary interactions.

Save screenshots under:

- `D:\projects-test\cgc-pms\.agent-runtime\reports\task-014-screenshots\`

## Acceptance Criteria

- Project management pages follow the new UI language and remain functional.
- Target management pages follow the new UI language and remain functional.
- Existing navigation and route titles remain valid.
- `目标成本管理` is not reintroduced.
- Focused redesign test is added and passes.
- Existing sidebar/mobile/dashboard/contract reference tests still pass.
- `pnpm build` passes.
- Browser verification confirms desktop and mobile layouts do not overflow.

## Expected Implementation Report

Write:

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-014-project-target-ui-redesign-implementation-result.md`

Include:

- status;
- summary;
- changed files;
- red/green TDD evidence;
- verification commands and exit codes;
- browser verification evidence and screenshot paths;
- known risks and intentional deviations.

## Rework History

None yet.

