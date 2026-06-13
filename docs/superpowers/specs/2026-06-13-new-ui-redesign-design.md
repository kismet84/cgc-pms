# New UI Redesign Design

## Status

Approved for implementation planning.

## User Decisions

- Visual direction: `B. 清爽企业级工作台`.
- First implementation slice: `应用外壳 + 首页驾驶舱 + 合同台账`.
- Sidebar rename: `目标成本管理` becomes `目标管理`.
- Approved concept version: v2.

## Concept Assets

- App shell + dashboard:
  - `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-app-shell-dashboard-v2.png`
- Contract ledger:
  - `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-contract-ledger-v2.png`

## Product Context

The app is a construction general contractor project management system. It serves project managers, business managers, cost managers, finance users, and executives who need to monitor projects, contracts, costs, settlement, payments, inventory, approvals, and alerts.

The redesign must remain a dense enterprise work tool. It should improve clarity, hierarchy, consistency, and perceived quality without turning the app into a marketing page or decorative dashboard.

## Frontend Context

- Framework: Vue 3 + Vite + TypeScript.
- UI libraries: Ant Design Vue, VXE Table, ECharts.
- Main frontend path: `frontend-admin`.
- Relevant first-slice files:
  - `frontend-admin/src/App.vue`
  - `frontend-admin/src/assets/styles/global.css`
  - `frontend-admin/src/layouts/BasicLayout.vue`
  - `frontend-admin/src/layouts/components/SidebarMenu.vue`
  - `frontend-admin/src/pages/dashboard/index.vue`
  - `frontend-admin/src/pages/contract/ContractLedgerPage.vue`

Ant Design Vue supports theme customization through `ConfigProvider` and design tokens, so global visual changes should prefer tokens before one-off CSS overrides where practical. Source: https://www.antdv.com/docs/vue/customize-theme

## Information Architecture

The sidebar should be ordered by work frequency and construction business flow:

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

Rationale:

- `首页` stays first as the operational entry point.
- Project and target planning come before cost tracking.
- Contract lifecycle modules follow the project/cost setup.
- Payment, subcontracting, procurement, inventory, and invoice modules support execution.
- Approval and alert modules remain high-visibility workflow entry points.
- Foundation/admin modules move to the bottom.

## Visual Direction

The approved direction is a clean light enterprise workspace:

- True light gray page background.
- White or very pale gray sidebar and topbar.
- White content surfaces.
- Ink text for primary content.
- Muted slate labels.
- Clear blue primary action and active state.
- Restrained semantic red, orange, and green.
- 6-8px radius for cards, panels, controls, and selected nav backgrounds.
- Subtle borders as the main separation mechanism.
- Minimal shadows only where layering is necessary.
- No dark sidebar, no beige/cream dominant palette, no decorative gradient orbs, no bokeh backgrounds, no marketing hero treatment.

## Design Tokens

Initial token direction:

- Primary: `#1668dc`
- Primary hover: `#155bd4`
- Page background: `#f5f7fb`
- Surface: `#ffffff`
- Surface subtle: `#f8fafc`
- Border: `#e4e9f2`
- Border subtle: `#eef2f7`
- Text: `#172033`
- Text secondary: `#475569`
- Text muted: `#7b8798`
- Success: `#16a34a`
- Warning: `#f59e0b`
- Error: `#dc2626`
- Info: `#0ea5e9`
- Radius small: `6px`
- Radius medium: `8px`
- Header height: `56px`
- Sidebar width: about `216px`
- Collapsed sidebar width: about `64px`

Use `a-config-provider` theme tokens in `App.vue` where possible for primary color, border radius, base font, control heights, table/header tones, and component state colors. Keep custom shell/page CSS in `global.css` and scoped component CSS.

## Typography

Use the current Chinese system font stack:

`-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif`

Typography guidance:

- Page title: 22-24px, 700 weight.
- Section title: 15-16px, 700 weight.
- KPI value: 22-28px, 750-800 weight, tabular numbers.
- KPI label: 13px, 500 weight.
- Table header: 12-13px, 600 weight.
- Table body: 13px.
- Control text: 13-14px, deliberate and consistent.
- No viewport-scaled font sizing.
- Letter spacing stays `0`.

## Application Shell

The shell should keep the existing two-column admin structure:

- Fixed left sidebar.
- Sticky topbar.
- Main content area with stable left offset.
- Collapsed sidebar state preserved.

Sidebar requirements:

- Light surface.
- Brand lockup with simple building mark and full product name.
- New sidebar order from this spec.
- `目标成本管理` label changes to `目标管理`.
- Active top-level item and active child item must be visually distinct.
- Parent items should remain compact and scannable.
- Collapsed state should keep icon alignment stable.

Topbar requirements:

- White surface with subtle bottom border.
- Menu collapse control on the left.
- Utility actions on the right: notifications, help, user profile.
- Notification unread state should remain visible.
- User block should stay compact and not dominate the header.

## Dashboard

The dashboard should feel like the operational starting point, not a promotional landing page.

Required structure:

- Breadcrumb or page title area.
- Project selector for project-scoped roles.
- Role tabs:
  - `项目总`
  - `商务经理`
  - `成本经理`
  - `财务`
  - `管理层`
- KPI strip with role-specific metrics.
- Chart/panel region.
- Compact data panels for tasks, lagging projects, approvals, expiring contracts, risk lists, or rankings depending on role.

Design requirements:

- Keep panels un-nested.
- Keep KPI cards compact and readable.
- Use semantic color only for meaning, not decoration.
- Chart containers should align with table/panel surfaces.
- Tables should use consistent compact row height and muted headers.
- Empty states should be quiet and fit inside the panel rhythm.

## Contract Ledger

The contract ledger is the dense work-tool benchmark for the redesign.

Required structure:

- Breadcrumb: `合同管理 / 合同台账`
- Title: `合同台账`
- Filter area:
  - `项目名称`
  - `合同类型`
  - `合同状态`
  - `合作方`
  - `合同编号`
  - `签订日期`
  - actions: `查询`, `重置`, `展开`
- KPI strip:
  - `合同总数`
  - `合同总金额(含税)`
  - `已付款金额`
  - `未付款金额`
  - `逾期合同数`
- Toolbar:
  - `新建合同`
  - `导出`
  - `列设置`
  - refresh icon action where useful
- Table:
  - `合同编号`
  - `合同名称`
  - `合同类型`
  - `合作方`
  - `合同金额(含税)`
  - `签订日期`
  - `合同状态`
  - `操作`
- Pagination.
- Right-side analysis rail:
  - `合同类型分布`
  - `合同状态统计`
  - `逾期预警`

Design requirements:

- Filter area should feel like a tool surface, not a large form card.
- KPI strip should be compact and horizontally scannable.
- Table density should support repeated daily use.
- Row hover and selected state should be visible but restrained.
- Right analysis rail should support quick inspection without stealing table width.
- Avoid mock-only statistics in implementation unless already backed by data or existing mock arrays.

## Component Rules

Use repeated component families instead of one-off CSS:

- Shell sidebar item.
- Shell topbar action.
- Page header.
- Filter surface.
- KPI card.
- Data panel.
- Analysis rail panel.
- Compact table treatment.
- Status tag treatment.
- Empty state.

Cards should have radius `8px` or less. Do not place cards inside cards. Page sections should be unframed layouts or single-level panels.

## Interaction Requirements

Preserve existing interactions:

- Sidebar collapse.
- Sidebar navigation.
- Dashboard role tabs.
- Dashboard project selector.
- Dashboard chart click/drill-down behavior.
- Contract filters.
- Contract table refresh/search/reset.
- Contract column settings.
- Contract pagination.
- Contract create navigation.

Add or preserve visual states:

- Active nav item.
- Active parent nav item.
- Selected role tab.
- Button hover/focus.
- Table row hover.
- Disabled/loading states.
- Notification unread indicator.

## Responsive Requirements

First implementation should support:

- Desktop around 1440x900.
- Smaller laptop around 1280x720.
- Tablet-ish width around 1024px.

Minimum behavior:

- Sidebar collapse keeps content usable.
- Contract ledger right rail can move below the table at narrower widths.
- KPI grids wrap without text overflow.
- Tables retain horizontal scroll where needed.
- Header text and controls do not overlap.

## Accessibility

- Keep semantic buttons for actions.
- Preserve keyboard-focus visibility.
- Keep text contrast suitable for enterprise work.
- Do not rely only on color for status.
- Keep icons paired with accessible labels or recognizable context.
- Respect reduced motion if animation is added later.

## Implementation Boundaries

Allowed in first slice:

- Add theme tokens in `App.vue`.
- Add or refine shared CSS variables in `global.css`.
- Update layout shell styling and navigation order/labeling.
- Update dashboard visual structure and repeated UI treatments.
- Update contract ledger visual structure and repeated UI treatments.
- Add focused frontend tests for sidebar order/labels and key rendering where practical.

Not in first slice:

- Full all-page redesign.
- Backend behavior changes.
- New product modules.
- Replacing Ant Design Vue or VXE Table.
- Adding a new design framework.
- Rewriting routing beyond navigation order/label changes needed for the sidebar.

## Verification Plan

Before claiming implementation complete:

- `pnpm build`
- Focused unit tests for `SidebarMenu` and any updated helpers.
- In-app browser verification on:
  - `/dashboard`
  - `/contract/ledger`
- Browser screenshots at desktop and smaller viewport.
- Visual fidelity comparison against:
  - `concept-app-shell-dashboard-v2.png`
  - `concept-contract-ledger-v2.png`
- Check at least:
  - sidebar order and label rename,
  - light shell and active states,
  - dashboard density and panel rhythm,
  - contract filter/KPI/table/rail layout,
  - typography/control sizing,
  - mobile or narrow-width overflow.

## Open Risks

- Image-generated concepts may not reproduce Chinese text perfectly. The implementation should use this spec's exact text and route labels, not imperfect in-image text.
- Some visual details in concept images may be decorative or approximate; code-native implementation should prioritize the approved system rules and existing functionality.
- Full product consistency will require later slices for the remaining pages.

## Approval

User approved the v2 concept direction on 2026-06-13.
