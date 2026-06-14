# task-011-dashboard-reference-fidelity

## Title

Bring the dashboard home page up to the approved v2 reference design fidelity.

## Status

Implementation completed by main agent; assigned to testing.

## User Request

User asked why the current `/dashboard` page does not match the approved reference image, then requested to continue.

## Goal

Update `frontend-admin/src/pages/dashboard/index.vue` so the project-manager home dashboard visually and structurally matches the approved `concept-app-shell-dashboard-v2.png` more closely, instead of only applying a light style refresh to the old dashboard structure.

## Approved Reference

- `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-app-shell-dashboard-v2.png`
- Design spec: `D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-13-new-ui-redesign-design.md`
- Prior implementation report: `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-new-ui-redesign-implementation-result.md`
- Prior test report: `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-new-ui-redesign-test-report.md`

## Root Cause From Main-Agent Inspection

The current implementation passed task-010 because the acceptance criteria were too broad:

- It verified that `/dashboard` renders.
- It verified sidebar order, role tabs, KPI cards, and responsive shell behavior.
- It did not verify fidelity against the approved reference layout.

Current `/dashboard` still mostly uses the old role-specific dashboard structure:

- Header, project selector, and role tabs are present.
- KPI cards are present.
- The project-manager view only renders task/approval/lagging/expiring panels.
- The approved reference's middle chart region is missing.
- The approved reference's denser bottom three-column table rhythm is missing.

The live page also often shows real empty data (`0`, `暂无数据`), which reduces visual density. Do not solve that by changing backend behavior. Use existing data where available and quiet fallback/sample visual rows only if they are clearly local presentation fallback for empty dashboard sections and do not affect business state.

## Scope

Primary implementation file:

- `D:\projects-test\cgc-pms\frontend-admin\src\pages\dashboard\index.vue`

Allowed supporting test file:

- `D:\projects-test\cgc-pms\frontend-admin\src\pages\dashboard\__tests__\DashboardReferenceFidelity.test.ts`

Allowed supporting style changes only if necessary:

- `D:\projects-test\cgc-pms\frontend-admin\src\assets\styles\global.css`

Do not change backend behavior. Do not revert unrelated working-tree changes, including task-010 files or `.sisyphus/boulder.json`.

## Required Visual Structure

For the `项目总` dashboard view, implement a reference-like desktop structure:

1. Header area:
   - Breadcrumb/title area remains compact.
   - Project selector remains available on the right.
   - Role tabs remain visible and interactive.

2. KPI strip:
   - Four horizontal KPI cards at desktop width:
     - `待办任务`
     - `滞后项目`
     - `待审批`
     - `临期合同`
   - Include a small comparison line such as `较昨日` with semantic up/down indicator where reasonable.
   - Use compact icon blocks with blue/orange/green/red or equivalent semantic colors.

3. Middle reference panels:
   - Add `项目经营概览`.
   - Add `成本构成分析`.
   - Add `资金收支概览`.
   - Use ECharts already present in the project:
     - business overview: bar + line chart.
     - cost composition: doughnut/pie chart with a center total.
     - funding overview: compact summary stats plus line chart.
   - If backend data is unavailable for this role, derive stable presentation values from existing dashboard payloads where possible, otherwise use local fallback visual data with clear comments that it is empty-state/demo presentation for dashboard fidelity only.

4. Bottom reference panels:
   - Lay out three desktop columns:
     - `待办任务`
     - `滞后项目`
     - `临期合同（30天内到期）`
   - Tables should be compact, readable, and visually closer to the reference image than the current tall empty panels.
   - Use existing live data when non-empty.
   - For empty live data, keep quiet empty state or short local presentation fallback rows; do not create persistent fake business records.

5. Responsive behavior:
   - Desktop around `1440x900` should show the reference-like first viewport with KPI strip, middle panels, and the start of bottom panels.
   - Around the current in-app browser width near `937px`, the dashboard should remain usable without overlap and should degrade into fewer columns cleanly.
   - At `390x844`, no shell-level horizontal overflow may return.

## Testing Requirements

Follow TDD for the new dashboard fidelity behavior:

1. Add a focused dashboard source/render test before implementation.
2. The test must fail before the dashboard sections are added.
3. The test must pass after implementation.

Minimum test assertions:

- Dashboard source or component render contains section labels:
  - `项目经营概览`
  - `成本构成分析`
  - `资金收支概览`
  - `临期合同（30天内到期）`
- Project-manager dashboard still contains role labels:
  - `项目总`
  - `商务经理`
  - `成本经理`
  - `财务`
  - `管理层`
- The old label `目标成本管理` must not be reintroduced.

Run at minimum:

```powershell
cd frontend-admin
pnpm vitest run src/pages/dashboard/__tests__/DashboardReferenceFidelity.test.ts
pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts src/layouts/__tests__/BasicLayout.mobile.test.ts src/pages/dashboard/__tests__/DashboardReferenceFidelity.test.ts
pnpm build
```

## Browser Verification Required In Report

Development Agent should do browser verification if available. Testing Agent will independently verify after implementation.

Required browser checks:

- `/dashboard` at desktop around `1440x900` or wider:
  - no blank page;
  - no framework overlay;
  - no relevant console errors;
  - reference section labels visible;
  - three middle panels visible;
  - bottom table panels visible or beginning to enter the first viewport;
  - no horizontal overflow.
- `/dashboard` at current in-app browser-like width around `937px`:
  - no overlap;
  - content stacks cleanly;
  - no horizontal overflow.
- `/dashboard` at `390x844`:
  - mobile shell fix remains intact;
  - no shell-level horizontal overflow.

## Fidelity Ledger Required In Report

The implementation report must include at least five comparison points against:

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-app-shell-dashboard-v2.png`

Required points:

1. Sidebar/shell context remains compatible with task-010.
2. KPI strip density and icon/card styling.
3. Middle chart region: `项目经营概览`, `成本构成分析`, `资金收支概览`.
4. Bottom three-column table rhythm.
5. Responsive behavior at desktop, current browser width, and mobile.

Known intentional deviations, if any, must be listed explicitly.

## Acceptance Criteria

- `frontend-admin/src/pages/dashboard/index.vue` includes the approved reference-like project-manager dashboard layout.
- The section labels `项目经营概览`, `成本构成分析`, and `资金收支概览` are visible on `/dashboard` for the `项目总` role.
- Existing role tabs, project selector, and dashboard data fetching continue to work.
- Focused dashboard fidelity test is added and passes.
- Existing task-010 sidebar/mobile tests still pass.
- `pnpm build` passes.
- Browser visual verification confirms the reference layout is substantially closer than task-010 and does not regress mobile overflow.

## Expected Implementation Report

Write:

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-011-dashboard-reference-fidelity-implementation-result.md`

Include:

- status;
- summary;
- changed files;
- red/green TDD evidence;
- build/test commands and exit codes;
- browser verification evidence or documented browser limitation;
- fidelity ledger;
- known risks.
