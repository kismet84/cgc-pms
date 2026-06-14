# task-010-new-ui-redesign

## Title

Design a new UI system for the project.

## Status

Completed after Rework 1.

## User Request

Use the `Build Web Apps` plugin to design a completely new UI for the current project.

## Goal

Create an approved UI redesign direction and implementation spec for the existing Vue 3 admin frontend.

## Current Frontend Context

- Framework: Vue 3 + Vite + TypeScript.
- UI libraries: Ant Design Vue, VXE Table, ECharts.
- Main frontend path: `D:\projects-test\cgc-pms\frontend-admin`.
- Main shell files:
  - `frontend-admin/src/layouts/BasicLayout.vue`
  - `frontend-admin/src/layouts/components/SidebarMenu.vue`
  - `frontend-admin/src/assets/styles/global.css`
  - `frontend-admin/src/App.vue`
- Routes are defined in `frontend-admin/src/router/index.ts`.
- The app is a construction general contractor project management admin system with dashboards, contracts, cost, settlement, projects, purchasing, payment, inventory, alerts, approvals, and system settings.

## Design Process Constraints

- Follow `Build Web Apps: frontend-app-builder`.
- Follow `superpowers:brainstorming`: no business code implementation before the design is presented and approved.
- Use Image Gen for visual concepts unless the user explicitly opts out.
- Keep the redesign suitable for a dense enterprise/admin product rather than a marketing landing page.
- Preserve core information architecture unless the approved design explicitly changes it.
- Use the local three-agent workflow only when dispatching implementation or testing work.

## Initial Design Questions

Answered:

- Use a browser-based visual companion for mockups and visual comparisons: yes.
- Preferred visual direction: `B. 清爽企业级工作台`.
- First implementation slice: `A. 应用外壳 + 首页驾驶舱 + 合同台账`.

## Selected Direction

`B. 清爽企业级工作台`

Design intent:

- Keep the product as a dense, efficient enterprise admin system.
- Refresh the app shell, navigation, typography, spacing, tables, filters, KPI cards, panels, and chart surfaces.
- Prefer light background, disciplined neutral palette, clear blue primary action, restrained semantic colors, flatter surfaces, and tighter information hierarchy.
- Preserve existing Vue 3 + Ant Design Vue + VXE Table + ECharts architecture.
- Use Ant Design Vue theme tokens where possible and scoped CSS/design tokens for custom layout surfaces.

## First Phase Scope

Implement after design approval:

- Application shell:
  - `frontend-admin/src/layouts/BasicLayout.vue`
  - `frontend-admin/src/layouts/components/SidebarMenu.vue`
  - `frontend-admin/src/assets/styles/global.css`
  - Ant Design Vue theme configuration in `frontend-admin/src/App.vue` if needed.
- Dashboard:
  - `frontend-admin/src/pages/dashboard/index.vue`
- Contract ledger:
  - `frontend-admin/src/pages/contract/ContractLedgerPage.vue`

Design concept artifacts to produce before implementation:

- App shell + dashboard concept.
- Contract ledger concept.
- Focused component/detail concept for navigation, filters, KPI cards, table density, and chart/panel surfaces if the first concepts are not readable enough.

## Concept Artifacts

- Approved app shell + dashboard concept v2:
  - `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-app-shell-dashboard-v2.png`
- Approved contract ledger concept v2:
  - `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-contract-ledger-v2.png`

Current review question:

- Does this clean enterprise workspace direction look right for the first implementation slice?
- If approved, write the UI design spec before implementation planning.

## Design Spec

`D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-13-new-ui-redesign-design.md`

User approved v2. The design spec has been written and should be reviewed before implementation planning begins.

## Implementation Plan

`D:\projects-test\cgc-pms\docs\superpowers\plans\2026-06-13-new-ui-redesign-implementation.md`

The first-slice implementation plan has been written. It covers theme tokens, sidebar order and rename, app shell refresh, dashboard refresh, contract ledger refresh, focused tests, browser verification, and local three-agent execution mapping.

## Development Assignment

`D:\projects-test\cgc-pms\.agent-runtime\messages\msg-014-task-010-dev-assignment.md`

Development agent should implement the first-slice UI redesign from the implementation plan and write:

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-new-ui-redesign-implementation-result.md`

## Implementation Report

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-new-ui-redesign-implementation-result.md`

Development agent reported `completed_with_browser_verification_gap`: focused `SidebarMenu` test and frontend build passed, while browser screenshot fidelity verification remains for the testing agent.

## Test Report

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-new-ui-redesign-test-report.md`

Testing agent reported `needs_fix` on 2026-06-13. Desktop browser acceptance and focused automated checks pass, but mobile browser acceptance fails due to app-shell horizontal overflow caused by the fixed 216px sidebar on a 390px viewport.

Updated on 2026-06-14: testing agent reported `pass_after_rework_1`. Rework 1 browser retest closed the mobile shell overflow defect on both `/dashboard` and `/contract/ledger` at `390x844`.

## Review Feedback

2026-06-13:

- User requested optimizing the sidebar ordering.
- User requested renaming `目标成本管理` to `目标管理`.
- Generated v2 concepts using the optimized sidebar order and renamed navigation item.

Recommended information architecture direction:

- Rename `目标成本管理` to `目标管理` in the redesigned navigation language.
- Reorder sidebar by work frequency and construction business flow rather than by current route declaration order.
- Keep `首页` first.
- Put project/cost/contract lifecycle modules before supporting modules.
- Move low-frequency administration modules to the bottom.

Recommended sidebar order after rename:

1. `首页`
2. `项目管理`
3. `目标管理`
4. `成本管理`
5. `合同管理`
6. `变更签证`
7. `结算管理`
8. `付款管理`
9. `分包管理`
10. `采购管理`
11. `库存管理`
12. `发票管理`
13. `审批管理`
14. `预警中心`
15. `基础数据`
16. `组织架构`
17. `系统设置`

## Acceptance Criteria

- A design direction is selected.
- Visual concept artifacts are produced and reviewed.
- A written UI design spec is created before implementation.
- User approval is recorded before dispatching development work.
- If implementation proceeds, development and testing are coordinated through the local multi-agent workflow.

## Rework History

### Rework 1

Status: completed.

Defect:

- P1 mobile app shell does not adapt at narrow viewport widths.
- Affected routes:
  - `/dashboard`
  - `/contract/ledger`
- Failing viewport: `390x844`.
- Evidence from test report:
  - dashboard: sidebar `216px`, main `174px`, horizontal overflow `226px`.
  - contract ledger: sidebar `216px`, main `174px`, horizontal overflow `190px`.

Required fix:

- Update `frontend-admin/src/layouts/BasicLayout.vue` shell behavior for mobile/narrow viewports.
- At an appropriate mobile breakpoint, prevent the fixed desktop sidebar from occupying permanent layout width.
- Reset topbar and main content left offset on mobile so the content uses the viewport width.
- Preserve desktop behavior:
  - 216px expanded sidebar on desktop.
  - 64px collapsed sidebar on desktop.
  - sticky topbar.
  - existing sidebar navigation and collapse behavior.
- Preserve dashboard and contract ledger page functionality.

Suggested implementation direction:

- Add a mobile breakpoint around `768px`.
- On mobile, make the sidebar off-canvas or collapsed without reserving layout width.
- Set `.topbar` and `.main-content` `margin-left: 0` on mobile.
- Ensure the menu toggle still gives access to navigation.
- If using off-canvas behavior, add an overlay or clear transform state so users can open/close the menu without horizontal scrolling.

Required verification:

- Run focused sidebar test:
  - `cd frontend-admin && pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts`
- Run frontend build:
  - `cd frontend-admin && pnpm build`
- Browser-check:
  - `/dashboard` at desktop and `390x844`.
  - `/contract/ledger` at desktop and `390x844`.
- The mobile checks must show no shell-level horizontal overflow from the sidebar.

Expected report:

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-new-ui-redesign-implementation-result.md`

Update the existing implementation report with Rework 1 details, changed files, verification commands, browser checks if available, and known risks.

Development result:

- Rework 1 implementation report updated.
- Added mobile off-canvas shell behavior in `frontend-admin/src/layouts/BasicLayout.vue`.
- Added `frontend-admin/src/layouts/__tests__/BasicLayout.mobile.test.ts`.
- Focused `BasicLayout.mobile.test.ts` passed.
- Focused `SidebarMenu.test.ts` + `BasicLayout.mobile.test.ts` passed.
- `pnpm build` passed.
- Browser-level mobile overflow verification remains assigned to testing.

Testing result:

- Testing agent updated `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-new-ui-redesign-test-report.md`.
- Final verdict: `pass_after_rework_1`.
- Focused layout/sidebar tests passed: `2 passed`, `3 tests passed`.
- `vue-tsc --noEmit` passed.
- Equivalent temp-copy production build passed.
- Browser retest at `390x844`:
  - `/dashboard`: document width `390`, horizontal overflow `0`.
  - `/contract/ledger`: document width `390`, horizontal overflow `0`.
- Desktop regression sanity passed on both routes at `1440x1000`.

Residual caveat:

- The tester's real-workspace Vite build probe was blocked by sandbox write permission on generated `frontend-admin/src/components.d.ts`; the equivalent temp-copy production build passed.
