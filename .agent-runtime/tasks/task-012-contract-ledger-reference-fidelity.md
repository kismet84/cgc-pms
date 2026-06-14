# task-012-contract-ledger-reference-fidelity

## Title

Bring the contract ledger page up to the approved v2 reference design fidelity.

## Status

Completed with environment note.

## User Request

User pointed out that the contract ledger page also needs to match the approved reference image.

## Goal

Update `frontend-admin/src/pages/contract/ContractLedgerPage.vue` so `/contract/ledger` visually and structurally matches the approved `concept-contract-ledger-v2.png` more closely, instead of only applying a light surface refresh.

## Approved Reference

- `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-contract-ledger-v2.png`
- Design spec: `D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-13-new-ui-redesign-design.md`
- Prior implementation report: `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-new-ui-redesign-implementation-result.md`
- Prior test report: `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-new-ui-redesign-test-report.md`

## Context

The approved contract ledger concept is the dense work-tool benchmark for the redesign. It shows:

- Light app shell from task-010.
- Breadcrumb `合同管理 / 合同台账`.
- Title `合同台账`.
- Compact two-row filter surface.
- Five KPI cards.
- Main table and toolbar in the left work area.
- Right analysis rail with:
  - `合同类型分布`
  - `合同状态统计`
  - `逾期预警`
- Dense table rows, status tags, row hover/selection feel, pagination, and compact action links.

The task-010 implementation refreshed the contract page, but the next pass must raise the acceptance bar from "surfaces render" to "reference image fidelity".

## Scope

Primary implementation file:

- `D:\projects-test\cgc-pms\frontend-admin\src\pages\contract\ContractLedgerPage.vue`

Allowed supporting test file:

- `D:\projects-test\cgc-pms\frontend-admin\src\pages\contract\__tests__\ContractLedgerReferenceFidelity.test.ts`

Allowed supporting style changes only if necessary:

- `D:\projects-test\cgc-pms\frontend-admin\src\assets\styles\global.css`

Do not change backend behavior. Do not change task-011 dashboard work unless absolutely necessary for shared style consistency. Do not revert unrelated working-tree changes, including `.sisyphus/boulder.json`.

## Required Visual Structure

Implement or refine the contract ledger page to match the reference concept:

1. Header:
   - Breadcrumb: `合同管理 / 合同台账`.
   - Page title: `合同台账`.
   - No marketing copy or extra explanatory text.

2. Filter surface:
   - Compact white bordered tool surface.
   - Fields visible at desktop:
     - `项目名称`
     - `合同类型`
     - `合同状态`
     - `合作方`
     - `合同编号`
     - `签订日期`
   - Actions:
     - `查询`
     - `重置`
     - `展开` or equivalent compact toggle if the existing form supports it.
   - Use deliberate 13-14px control typography.
   - Avoid large card-like vertical padding.

3. KPI strip:
   - Five compact horizontal cards:
     - `合同总数`
     - `合同总金额(含税)`
     - `已付款金额`
     - `未付款金额`
     - `逾期合同数`
   - Icon blocks should be visually close to the reference: compact square/rounded icons with semantic blue/green/blue-orange/red treatment.
   - Values should use tabular numeric emphasis and consistent units.

4. Main table work area:
   - Toolbar above table:
     - `新建合同`
     - `导出`
     - `列设置`
     - refresh icon/action.
   - Table columns should align with the reference where existing data supports them:
     - selection checkbox;
     - `合同编号`;
     - `合同名称`;
     - `合同类型`;
     - `合作方`;
     - `合同金额(含税)`;
     - `签订日期`;
     - `合同状态`;
     - `操作`.
   - Keep existing search/reset/pagination/column settings/create navigation behavior.
   - Use compact row height and muted header styling.
   - Status tags should be restrained and readable.

5. Right analysis rail:
   - Desktop right rail must show three stacked panels:
     - `合同类型分布`
     - `合同状态统计`
     - `逾期预警`
   - Use ECharts already present in the project for two doughnut charts if available in current code, or add them locally through existing `vue-echarts` patterns.
   - `逾期预警` should show a compact warning list using live data where available.
   - If live analysis data is empty, use quiet local presentation fallback only for visual fidelity; do not persist fake business records or alter API responses.

6. Responsive behavior:
   - Around `1440x900`, the page should show filter, KPI strip, table, and right rail in one coherent first viewport.
   - Around current in-app browser width near `937px`, right rail may move below the table; no overlap.
   - At `390x844`, the task-010 mobile shell fix must remain intact and no shell-level horizontal overflow may return. Table-level horizontal scroll is acceptable where needed.

## Testing Requirements

Follow TDD for the new contract ledger fidelity behavior:

1. Add a focused contract ledger source/render test before implementation.
2. The test must fail before the missing fidelity structure is added or tightened.
3. The test must pass after implementation.

Minimum test assertions:

- Contract ledger source or component render contains section labels:
  - `合同类型分布`
  - `合同状态统计`
  - `逾期预警`
  - `合同总金额(含税)`
  - `未付款金额`
- Toolbar action labels remain present:
  - `新建合同`
  - `导出`
  - `列设置`
- Header labels remain present:
  - `合同管理`
  - `合同台账`
- The old label `目标成本管理` must not be reintroduced anywhere by this task.

Run at minimum:

```powershell
cd frontend-admin
pnpm vitest run src/pages/contract/__tests__/ContractLedgerReferenceFidelity.test.ts
pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts src/layouts/__tests__/BasicLayout.mobile.test.ts src/pages/contract/__tests__/ContractLedgerReferenceFidelity.test.ts
pnpm build
```

## Browser Verification Required In Report

Development Agent should do browser verification if available. Testing Agent will independently verify after implementation.

Required browser checks:

- `/contract/ledger` at desktop around `1440x900` or wider:
  - no blank page;
  - no framework overlay;
  - no relevant console errors;
  - filter surface visible and compact;
  - five KPI cards visible;
  - main table toolbar and rows/empty state visible;
  - right analysis rail visible with all three required panel titles;
  - no horizontal overflow.
- `/contract/ledger` at current in-app browser-like width around `937px`:
  - right rail stacks or adapts cleanly;
  - no overlap;
  - no shell-level horizontal overflow.
- `/contract/ledger` at `390x844`:
  - mobile shell fix remains intact;
  - no shell-level horizontal overflow.

## Fidelity Ledger Required In Report

The implementation report must include at least five comparison points against:

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-contract-ledger-v2.png`

Required points:

1. Header/breadcrumb/title alignment.
2. Filter surface density and field/action arrangement.
3. KPI strip count, icon treatment, values, and spacing.
4. Table toolbar, table density, status/action treatment.
5. Right analysis rail with two doughnut panels and overdue warning list.
6. Responsive behavior at desktop, current browser width, and mobile.

Known intentional deviations, if any, must be listed explicitly.

## Acceptance Criteria

- `frontend-admin/src/pages/contract/ContractLedgerPage.vue` includes the approved reference-like contract ledger layout.
- Required section labels and toolbar labels are visible on `/contract/ledger`.
- Existing contract search/reset, pagination, column settings, and create navigation behavior continue to work.
- Focused contract ledger fidelity test is added and passes.
- Existing task-010 sidebar/mobile tests still pass.
- `pnpm build` passes.
- Browser visual verification confirms the page is substantially closer to the approved reference and does not regress mobile overflow.

## Expected Implementation Report

Write:

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-012-contract-ledger-reference-fidelity-implementation-result.md`

Include:

- status;
- summary;
- changed files;
- red/green TDD evidence;
- build/test commands and exit codes;
- browser verification evidence or documented browser limitation;
- fidelity ledger;
- known risks.

## Implementation Report

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-012-contract-ledger-reference-fidelity-implementation-result.md`

Development Agent reported `completed_with_browser_verification_gap`.

Summary:

- Updated `frontend-admin/src/pages/contract/ContractLedgerPage.vue`.
- Added `frontend-admin/src/pages/contract/__tests__/ContractLedgerReferenceFidelity.test.ts`.
- Added or tightened the reference-style header, filter surface, five KPI cards, toolbar/table density, and right analysis rail.
- Added a second ECharts doughnut chart for `合同状态统计`.
- Kept analysis fallback data local/presentation-only.

Verification reported by Development Agent:

- TDD red: focused contract fidelity test failed before implementation because `cl-analysis-rail` and `statusDonutOption` were missing.
- TDD green: focused contract fidelity test passed.
- Regression tests passed:
  - `pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts src/layouts/__tests__/BasicLayout.mobile.test.ts src/pages/contract/__tests__/ContractLedgerReferenceFidelity.test.ts`
  - Result: `3 passed`, `4 passed`.
- `pnpm build` passed with the existing large chunk warning.

Browser verification gap:

- Existing `localhost:5173` dev server appeared to serve stale no-query compiled output for the contract ledger SFC at some widths.
- Fresh production preview redirected to login and lacked authenticated local user state.
- Independent testing must restart/refresh the frontend serving path or otherwise ensure the current built module is loaded before judging visual fidelity.

## Testing Assignment

Testing Agent should verify:

- The implementation report above.
- Focused contract fidelity test.
- Existing sidebar/mobile regression tests.
- `pnpm build`.
- Authenticated browser visual fidelity for `/contract/ledger` after ensuring stale dev-server output is not being used.
- Desktop, current-width around `937px`, and mobile `390x844` behavior.

Expected test report:

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-012-contract-ledger-reference-fidelity-test-report.md`

## Test Report

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-012-contract-ledger-reference-fidelity-test-report.md`

Testing Agent reported `pass_with_environment_note`.

Summary:

- Focused contract ledger fidelity test passed: `1 passed`, `1 test passed`.
- Regression test set passed: `3 passed`, `4 tests passed`.
- `vue-tsc --noEmit` passed.
- Equivalent temp-copy production build passed.
- Authenticated browser visual verification passed at:
  - `1440x900`;
  - `937x900`;
  - `390x844`.

Browser evidence:

- `/contract/ledger` rendered without login redirect.
- Console errors: `0`.
- Page errors: `0`.
- Document-level horizontal overflow: `0` for all tested viewports.
- Desktop right rail showed `合同类型分布`, `合同状态统计`, and `逾期预警`.
- Right rail stacked below table at `937px`.
- Mobile shell stayed off-canvas with `main_margin_left: 0px` and document width `390`.

Environment note:

- The testing Agent's real-workspace Vite build probe was blocked by the tester sandbox while writing generated `src/components.d.ts`.
- Equivalent temp-copy production build passed.
- Main Agent reran `pnpm build` in the real workspace successfully after receiving the report.

## Main-Agent Final Verification

2026-06-14:

- Command:
  - `cd frontend-admin && pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts src/layouts/__tests__/BasicLayout.mobile.test.ts src/pages/contract/__tests__/ContractLedgerReferenceFidelity.test.ts --configLoader runner`
- Result:
  - exit code `0`;
  - `3 passed`;
  - `4 passed`.
- Command:
  - `cd frontend-admin && pnpm build`
- Result:
  - exit code `0`;
  - production build completed successfully;
  - existing large chunk warning remains.
