# task-010-new-ui-redesign

## Title

Design a new UI system for the project.

## Status

Design spec written; awaiting user review before implementation planning.

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

None.
