# task-010-new-ui-redesign implementation_result

## Routing

- message_id: `msg-015`
- task_id: `task-010-new-ui-redesign`
- from: `019ebc4c-8e43-70d3-bc4e-8cf25548cd68`
- to: `019ebc4b-a8d8-7a61-9fd3-b6b5e2a172db`
- type: `implementation_result`

## Status

`completed_with_browser_verification_gap`

## Summary

Implemented the first-stage UI redesign slice for:

- Theme tokens and global enterprise UI primitives.
- Sidebar information architecture, visible rename, approved ordering, icon coverage, and regression test.
- Light app shell, 216px sidebar, code-native brand lockup, sticky topbar, and collapsed offset.
- Dashboard first-slice visual refresh with compact header, segmented role tabs, responsive KPI grid, and shared panel styling.
- Contract ledger first-slice visual refresh with page header, compact filter surface, KPI strip, polished table surface, right rail styling, and responsive behavior.

No backend behavior was changed for this task.

## Changed Files

- `D:\projects-test\cgc-pms\frontend-admin\src\App.vue`
- `D:\projects-test\cgc-pms\frontend-admin\src\assets\styles\global.css`
- `D:\projects-test\cgc-pms\frontend-admin\src\router\index.ts`
- `D:\projects-test\cgc-pms\frontend-admin\src\layouts\BasicLayout.vue`
- `D:\projects-test\cgc-pms\frontend-admin\src\layouts\__tests__\BasicLayout.mobile.test.ts`
- `D:\projects-test\cgc-pms\frontend-admin\src\layouts\components\SidebarMenu.vue`
- `D:\projects-test\cgc-pms\frontend-admin\src\layouts\components\__tests__\SidebarMenu.test.ts`
- `D:\projects-test\cgc-pms\frontend-admin\src\pages\dashboard\index.vue`
- `D:\projects-test\cgc-pms\frontend-admin\src\pages\contract\ContractLedgerPage.vue`

## Visual Summary

- Added Ant Design Vue token baseline in `App.vue`, aligning primary, semantic colors, typography, radius, table, tabs, and control heights.
- Replaced global root variables with the approved enterprise workspace palette and shell dimensions.
- Added shared `.app-page`, `.app-page-title`, `.app-panel`, `.app-panel-header`, `.app-kpi-value`, `.app-muted`, and `.app-table-link` primitives.
- Reordered root sidebar entries to start with `首页`, `项目管理`, `目标管理`, `成本管理`, `合同管理`, `变更签证`, `结算管理`, `付款管理`.
- Renamed visible cost-target labels from `目标成本管理` to `目标管理`, including the visible child menu label caught by the focused test.
- Refreshed the app shell to a light sidebar/topbar design with CSS-variable driven widths and offsets.
- Updated dashboard cards and panels to the clean enterprise surface system while preserving role switching, data fetching, charts, drill-down modal, and tables.
- Updated contract ledger filter/KPI/table/right-rail surfaces and responsive layout while preserving data fetching, search/reset, pagination, column toggles, and create navigation behavior.

## Verification

### Focused Unit Test

- Command: `cd frontend-admin && pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts`
- First run exit code: `1`
- First run failure: test caught remaining visible `目标成本管理` label from the cost-target child route.
- Fix: renamed the visible child route title to `目标管理`.
- Final exit code: `0`
- Final result: `Test Files 1 passed (1)`, `Tests 2 passed (2)`.

### Frontend Build

- Command: `cd frontend-admin && pnpm build`
- Exit code: `0`
- Result: `vue-tsc --noEmit && vite build` completed successfully.
- Note: Vite emitted the existing large chunk-size warning; the implementation plan explicitly allows existing bundle-size warnings.

## Browser Routes Checked

Not executed in this development thread.

Reason:

- The listed in-app browser skill file was unavailable in the current environment.
- Tool discovery did not expose a direct browser navigation/screenshot tool, only `node_repl` support intended to be used with a browser plugin.
- I did not start a separate dev server and simulate browser screenshots because the task requires local multi-agent coordination and the testing Agent is expected to perform independent visual/browser verification after this report.

Recommended follow-up browser routes for the testing Agent:

- `http://localhost/dashboard`
- `http://localhost/contract/ledger`

## Known Risks

- Browser screenshot fidelity comparison against the approved v2 concept images was not performed in this thread.
- Visual behavior such as collapsed sidebar alignment, chart drill-down interaction, and responsive contract right-rail layout should be independently checked by the testing Agent in a running browser.
- The workspace already contains unrelated uncommitted files under `.agent-runtime/`, `.sisyphus/`, and generated `frontend-admin/.pnpm-store/`; this task intentionally changed only the files listed above.

## Rework 1 - Mobile Shell Overflow Fix

### Status

`completed_with_browser_verification_gap`

### Defect

Testing Agent reported mobile shell horizontal overflow at `390x844`:

- `/dashboard`: fixed `216px` sidebar left only `174px` for main content and produced `226px` horizontal overflow.
- `/contract/ledger`: fixed `216px` sidebar left only `174px` for main content and produced `190px` horizontal overflow.

### Root Cause

`BasicLayout.vue` used desktop shell offsets at all viewport widths:

- `.sidebar` was always fixed at the desktop `216px` width.
- `.topbar` and `.main-content` always kept `margin-left: var(--shell-sidebar-width)`.
- The collapsed desktop selector still reserved `64px`.

At a `390px` mobile viewport, the fixed desktop sidebar permanently consumed shell width and forced horizontal overflow.

### Fix

Updated `D:\projects-test\cgc-pms\frontend-admin\src\layouts\BasicLayout.vue`:

- Added mobile state tracking with `window.matchMedia('(max-width: 768px)')`.
- On mobile, automatically collapses the shell navigation state so the desktop sidebar is not permanently open.
- Added mobile off-canvas sidebar behavior under `@media (max-width: 768px)`.
- Reset `.topbar` and `.main-content` `margin-left` to `0` on mobile.
- Overrode the desktop collapsed offset selector on mobile so it also uses `margin-left: 0`.
- Added a mobile overlay mask for open navigation; tapping the mask closes the sidebar.
- Preserved desktop behavior:
  - `216px` expanded sidebar.
  - `64px` collapsed sidebar.
  - sticky topbar.
  - existing hamburger toggle.
  - existing sidebar menu behavior.

Added `D:\projects-test\cgc-pms\frontend-admin\src\layouts\__tests__\BasicLayout.mobile.test.ts`:

- Source-level regression test verifies:
  - `@media (max-width: 768px)` exists.
  - topbar/main-content mobile offset is reset to `margin-left: 0`.
  - sidebar becomes off-canvas with `transform: translateX(-100%)`.
  - collapsed desktop offset is overridden to `margin-left: 0` on mobile.

### Rework Verification

#### Red Test

- Command: `cd frontend-admin && pnpm vitest run src/layouts/__tests__/BasicLayout.mobile.test.ts`
- Exit code before fix: `1`
- Failure: expected `@media (max-width: 768px)` was missing from `BasicLayout.vue`.

#### Focused Tests

- Command: `cd frontend-admin && pnpm vitest run src/layouts/__tests__/BasicLayout.mobile.test.ts`
- Exit code: `0`
- Result: `Test Files 1 passed (1)`, `Tests 1 passed (1)`.

- Command: `cd frontend-admin && pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts src/layouts/__tests__/BasicLayout.mobile.test.ts`
- Exit code: `0`
- Result: `Test Files 2 passed (2)`, `Tests 3 passed (3)`.

#### Frontend Build

- Command: `cd frontend-admin && pnpm build`
- Exit code: `0`
- Result: `vue-tsc --noEmit && vite build` completed successfully.
- Note: Vite emitted the existing large chunk-size warning; this warning is unrelated to Rework 1.

### Browser Checks

Not executed in this development thread.

Reason:

- The current thread still does not expose a direct in-app browser navigation/screenshot tool.
- The prior implementation report already documented this tool gap.
- The Rework 1 CSS regression test and build verification passed, but the testing Agent should independently browser-check:
  - `/dashboard` at desktop and `390x844`.
  - `/contract/ledger` at desktop and `390x844`.
  - Confirm no shell-level horizontal overflow from the sidebar.

### Rework 1 Known Risks

- Browser-level overflow measurement was not captured in this thread due to unavailable browser tooling.
- The mobile navigation uses the existing collapse boolean for off-canvas open/close; this preserves the existing hamburger interaction but should be visually checked in a real browser.
