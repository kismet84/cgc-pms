# New UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved first-slice UI redesign for the app shell, dashboard, and contract ledger.

**Architecture:** Keep the existing Vue 3 + Ant Design Vue + VXE Table + ECharts stack. Add a shared token layer in `App.vue` and `global.css`, then apply the approved clean enterprise workspace system to the shell, sidebar navigation, dashboard, and contract ledger without changing backend behavior.

**Tech Stack:** Vue 3, TypeScript, Vite, Ant Design Vue 4, VXE Table, ECharts, Vitest, Vue Test Utils.

---

## Source Spec

Design spec:

`D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-13-new-ui-redesign-design.md`

Approved concept assets:

- `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-app-shell-dashboard-v2.png`
- `D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-contract-ledger-v2.png`

## File Structure

Modify these existing files:

- `frontend-admin/src/App.vue`
  - Owns Ant Design Vue `ConfigProvider` theme tokens.
- `frontend-admin/src/assets/styles/global.css`
  - Owns shared CSS variables, base body styling, scrollbar styling, shared utility classes, and global Ant/VXE polish.
- `frontend-admin/src/router/index.ts`
  - Owns the visible sidebar label rename from `目标成本管理` to `目标管理`.
- `frontend-admin/src/layouts/BasicLayout.vue`
  - Owns app shell, topbar, sidebar width, brand, and content offsets.
- `frontend-admin/src/layouts/components/SidebarMenu.vue`
  - Owns menu item building, sidebar order, icon map, selected/open state behavior, and sidebar menu styling.
- `frontend-admin/src/layouts/components/__tests__/SidebarMenu.test.ts`
  - Owns route key, visible label, and ordering regression tests.
- `frontend-admin/src/pages/dashboard/index.vue`
  - Owns first-slice dashboard visual treatment.
- `frontend-admin/src/pages/contract/ContractLedgerPage.vue`
  - Owns first-slice contract ledger visual treatment.

Create these files only if the implementation becomes too repetitive:

- `frontend-admin/src/components/ui/AppPageHeader.vue`
  - Reusable page header for title, breadcrumb, actions, and compact filters.
- `frontend-admin/src/components/ui/KpiMetricCard.vue`
  - Reusable KPI metric card for dashboard and contract ledger.

Do not create a new design framework. Prefer shared CSS classes and small Vue components only where they remove real duplication.

## Task 1: Theme Tokens And Global Surface

**Files:**

- Modify: `frontend-admin/src/App.vue`
- Modify: `frontend-admin/src/assets/styles/global.css`

- [ ] **Step 1: Add Ant Design Vue theme tokens**

In `frontend-admin/src/App.vue`, change the config provider to include the approved token baseline:

```vue
<script setup lang="ts">
import { RouterView } from 'vue-router'
import zhCN from 'ant-design-vue/es/locale/zh_CN'

const theme = {
  token: {
    colorPrimary: '#1668dc',
    colorInfo: '#0ea5e9',
    colorSuccess: '#16a34a',
    colorWarning: '#f59e0b',
    colorError: '#dc2626',
    colorText: '#172033',
    colorTextSecondary: '#475569',
    colorTextTertiary: '#7b8798',
    colorBorder: '#e4e9f2',
    colorBgLayout: '#f5f7fb',
    colorBgContainer: '#ffffff',
    borderRadius: 8,
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif",
    controlHeight: 34,
  },
  components: {
    Button: {
      borderRadius: 6,
      controlHeight: 34,
    },
    Input: {
      borderRadius: 6,
      controlHeight: 34,
    },
    Select: {
      borderRadius: 6,
      controlHeight: 34,
    },
    Table: {
      headerBg: '#f8fafc',
      headerColor: '#475569',
      rowHoverBg: '#f4f8ff',
      borderColor: '#e4e9f2',
    },
    Tabs: {
      itemSelectedColor: '#1668dc',
      inkBarColor: '#1668dc',
    },
  },
}
</script>

<template>
  <a-config-provider :locale="zhCN" :theme="theme">
    <RouterView />
  </a-config-provider>
</template>
```

- [ ] **Step 2: Update global CSS variables**

In `frontend-admin/src/assets/styles/global.css`, replace the root token block with:

```css
:root {
  --primary: #1668dc;
  --primary-hover: #155bd4;
  --bg: #f5f7fb;
  --surface: #ffffff;
  --surface-subtle: #f8fafc;
  --border: #e4e9f2;
  --border-subtle: #eef2f7;
  --text: #172033;
  --text-secondary: #475569;
  --muted: #7b8798;
  --success: #16a34a;
  --warning: #f59e0b;
  --error: #dc2626;
  --info: #0ea5e9;
  --radius-sm: 6px;
  --radius-md: 8px;
  --shell-sidebar-width: 216px;
  --shell-sidebar-collapsed-width: 64px;
  --shell-header-height: 56px;
  --shadow-soft: 0 10px 28px rgba(15, 23, 42, 0.05);
}
```

- [ ] **Step 3: Add shared enterprise UI primitives**

Append these shared classes to `global.css`:

```css
.app-page {
  min-height: 100%;
}

.app-page-title {
  margin: 0;
  color: var(--text);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
}

.app-panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}

.app-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.app-kpi-value {
  color: var(--text);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}

.app-muted {
  color: var(--muted);
}

.app-table-link {
  color: var(--primary);
  font-weight: 500;
  text-decoration: none;
}
```

- [ ] **Step 4: Run a build smoke check**

Run:

```powershell
cd frontend-admin
pnpm build
```

Expected: build succeeds. Existing bundle-size warnings are acceptable.

## Task 2: Sidebar Information Architecture

**Files:**

- Modify: `frontend-admin/src/router/index.ts`
- Modify: `frontend-admin/src/layouts/components/SidebarMenu.vue`
- Modify: `frontend-admin/src/layouts/components/__tests__/SidebarMenu.test.ts`

- [ ] **Step 1: Rename the route label**

In `frontend-admin/src/router/index.ts`, find the route with `path: 'cost-target'` and change:

```ts
meta: { title: '目标成本管理', icon: 'AimOutlined' },
```

to:

```ts
meta: { title: '目标管理', icon: 'AimOutlined' },
```

- [ ] **Step 2: Add the approved sidebar order**

In `frontend-admin/src/layouts/components/SidebarMenu.vue`, add this constant near the icon map:

```ts
const MENU_ORDER = [
  '/dashboard',
  '/project',
  '/cost-target',
  '/cost',
  '/contract',
  '/variation',
  '/settlement',
  '/payment',
  '/subcontract',
  '/purchase',
  '/inventory',
  '/invoice',
  '/approval',
  '/alert',
  '/material',
  '/org',
  '/system',
]
```

- [ ] **Step 3: Sort visible root menu items by approved order**

Replace the current `menuItems` computed body with:

```ts
const menuItems = computed(() => {
  return routes
    .find((r) => r.path === '/')
    ?.children?.filter((r) => !r.meta?.hidden)
    .sort((a, b) => menuRank(resolveMenuPath('', a.path)) - menuRank(resolveMenuPath('', b.path)))
    .map((r) => buildMenuItem(r, ''))
})

function menuRank(path: string) {
  const index = MENU_ORDER.indexOf(path)
  return index === -1 ? MENU_ORDER.length : index
}
```

Keep the existing `resolveMenuPath` behavior so nested keys stay full paths.

- [ ] **Step 4: Expand the icon map for existing route icons**

Ensure `SidebarMenu.vue` imports and maps every icon used by root routes:

```ts
import {
  AccountBookOutlined,
  AimOutlined,
  AlertOutlined,
  ApartmentOutlined,
  AuditOutlined,
  BranchesOutlined,
  DatabaseOutlined,
  DollarOutlined,
  FileTextOutlined,
  HomeOutlined,
  InboxOutlined,
  ProjectOutlined,
  SettingOutlined,
  ShoppingCartOutlined,
  SwapOutlined,
} from '@ant-design/icons-vue'
```

Map them by exact string key:

```ts
const iconMap: Record<string, MenuItem['icon']> = {
  AccountBookOutlined,
  AimOutlined,
  AlertOutlined,
  ApartmentOutlined,
  AuditOutlined,
  BranchesOutlined,
  DatabaseOutlined,
  DollarOutlined,
  FileTextOutlined,
  HomeOutlined,
  InboxOutlined,
  ProjectOutlined,
  SettingOutlined,
  ShoppingCartOutlined,
  SwapOutlined,
}
```

- [ ] **Step 5: Add a sidebar order and rename test**

In `frontend-admin/src/layouts/components/__tests__/SidebarMenu.test.ts`, add this test:

```ts
it('orders root menu items by approved business flow and renames target cost', () => {
  const wrapper = mount(SidebarMenu, {
    global: {
      stubs: {
        'a-menu': AMenuStub,
      },
    },
  })

  const rootKeys = wrapper
    .findAll('[data-menu-key]')
    .map((node) => node.attributes('data-menu-key'))
    .filter((key) =>
      [
        '/dashboard',
        '/project',
        '/cost-target',
        '/cost',
        '/contract',
        '/variation',
        '/settlement',
        '/payment',
      ].includes(key),
    )

  expect(rootKeys).toEqual([
    '/dashboard',
    '/project',
    '/cost-target',
    '/cost',
    '/contract',
    '/variation',
    '/settlement',
    '/payment',
  ])

  expect(wrapper.text()).toContain('目标管理')
  expect(wrapper.text()).not.toContain('目标成本管理')
})
```

- [ ] **Step 6: Run focused sidebar test**

Run:

```powershell
cd frontend-admin
pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts
```

Expected: all `SidebarMenu` tests pass.

## Task 3: App Shell Visual Refresh

**Files:**

- Modify: `frontend-admin/src/layouts/BasicLayout.vue`
- Modify: `frontend-admin/src/layouts/components/SidebarMenu.vue`

- [ ] **Step 1: Update shell dimensions**

In `BasicLayout.vue`, set the sider width to match the design token:

```vue
<a-layout-sider
  v-model:collapsed="collapsed"
  :width="216"
  :collapsed-width="64"
  class="sidebar"
  theme="light"
>
```

- [ ] **Step 2: Replace the brand markup**

Use a code-native brand lockup:

```vue
<div class="brand" :class="{ 'brand--collapsed': collapsed }">
  <div class="logo" aria-hidden="true">
    <span class="logo-mark">建</span>
  </div>
  <span v-if="!collapsed" class="brand-text">建筑工程总包项目管理系统</span>
</div>
```

- [ ] **Step 3: Update shell CSS**

Replace the shell CSS values in `BasicLayout.vue` with these key rules:

```css
.basic-layout {
  min-height: 100vh;
  background: var(--bg);
}

.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 20;
  overflow: hidden;
  background: var(--surface) !important;
  border-right: 1px solid var(--border);
  box-shadow: none;
}

.brand {
  height: var(--shell-header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}

.brand--collapsed {
  justify-content: center;
  padding: 0;
}

.logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #1668dc, #0ea5e9);
  display: grid;
  place-items: center;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 8px 18px rgba(22, 104, 220, 0.22);
}

.logo-mark {
  font-size: 15px;
  font-weight: 800;
  line-height: 1;
}

.brand-text {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
  text-overflow: ellipsis;
}

.topbar {
  height: var(--shell-header-height);
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid var(--border);
  padding: 0 22px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: sticky;
  top: 0;
  z-index: 10;
  margin-left: var(--shell-sidebar-width);
  backdrop-filter: blur(8px);
}

.main-content {
  padding: 18px;
  margin-left: var(--shell-sidebar-width);
  min-height: calc(100vh - var(--shell-header-height));
  background: var(--bg);
}
```

Keep the collapsed left-offset rule, but use CSS variables:

```css
:deep(.ant-layout-sider-collapsed) + .ant-layout .topbar,
:deep(.ant-layout-sider-collapsed) + .ant-layout .main-content {
  margin-left: var(--shell-sidebar-collapsed-width);
}
```

- [ ] **Step 4: Refresh user/profile styling**

Keep the same dropdown behavior. Use compact text and neutral colors:

```css
.username {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
}

.role {
  margin-top: 1px;
  font-size: 12px;
  color: var(--muted);
}
```

- [ ] **Step 5: Update SidebarMenu visual states**

In `SidebarMenu.vue`, update menu styling:

```css
.sidebar-menu {
  border-right: none;
  padding: 10px 10px 14px;
  height: calc(100vh - var(--shell-header-height));
  overflow-y: auto;
  background: transparent;
}

:deep(.ant-menu) {
  color: var(--text-secondary);
  background: transparent;
}

:deep(.ant-menu-item),
:deep(.ant-menu-submenu-title) {
  height: 38px;
  line-height: 38px;
  margin: 3px 0;
  border-radius: var(--radius-md);
}

:deep(.ant-menu-item-selected) {
  background: #edf5ff !important;
  color: var(--primary) !important;
  font-weight: 700;
}

:deep(.ant-menu-item-selected::after) {
  display: none;
}
```

- [ ] **Step 6: Browser-check shell**

Run the app or use the deployed app, then check `/dashboard` in the in-app browser.

Expected:

- Sidebar is light.
- Sidebar order starts `首页`, `项目管理`, `目标管理`, `成本管理`, `合同管理`.
- Topbar remains sticky.
- Collapsed state keeps content aligned.

## Task 4: Dashboard First-Slice Refresh

**Files:**

- Modify: `frontend-admin/src/pages/dashboard/index.vue`

- [ ] **Step 1: Rename page wrapper classes**

Keep existing data-fetching, role tabs, ECharts options, drill-down modal, and table columns. Update only template structure and style classes where needed. The root should remain:

```vue
<div class="dashboard app-page">
```

- [ ] **Step 2: Update dashboard header area**

Replace the top breadcrumb/title/project selector area with a compact page header:

```vue
<div class="dashboard-header">
  <div>
    <a-breadcrumb class="breadcrumb">
      <a-breadcrumb-item>首页</a-breadcrumb-item>
      <a-breadcrumb-item>驾驶舱</a-breadcrumb-item>
    </a-breadcrumb>
    <h1 class="app-page-title">首页</h1>
  </div>
  <div v-if="activeRole !== 'mgmt'" class="project-field">
    <label>选择项目</label>
    <a-select v-model:value="selectedProjectId" placeholder="请选择项目" style="width: 260px">
      <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
        {{ p.projectName }}
      </a-select-option>
    </a-select>
  </div>
</div>
```

- [ ] **Step 3: Update role tab styling**

Keep the existing `a-tabs`, but style it as a compact segmented work switch:

```css
.role-tabs {
  margin: 14px 0;
}

.role-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.role-tabs :deep(.ant-tabs-tab) {
  padding: 8px 14px;
  font-size: 13px;
}
```

- [ ] **Step 4: Replace KPI card styles**

Keep existing KPI markup and icons. Update `.kpi-card`, `.kpi-icon`, `.kpi-title`, and `.kpi-value`:

```css
.kpi-card {
  min-height: 92px;
  padding: 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  display: flex;
  gap: 12px;
  align-items: flex-start;
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.kpi-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 15px;
  flex-shrink: 0;
}

.kpi-title {
  margin-bottom: 6px;
  color: var(--muted);
  font-size: 13px;
  font-weight: 500;
}

.kpi-value {
  color: var(--text);
  font-size: 23px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}
```

- [ ] **Step 5: Replace panel styles**

Update `.panel` and `.panel-header` to match shared panel rules:

```css
.panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.panel-header {
  min-height: 44px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 700;
}
```

- [ ] **Step 6: Add responsive dashboard grids**

Replace fixed KPI grid definitions with responsive tracks:

```css
.kpi-grid-4,
.kpi-grid-5,
.kpi-grid-6,
.kpi-grid-7 {
  grid-template-columns: repeat(auto-fit, minmax(178px, 1fr));
}

@media (max-width: 1100px) {
  .chart-row {
    grid-template-columns: 1fr;
  }
}
```

- [ ] **Step 7: Verify dashboard behavior**

Run:

```powershell
cd frontend-admin
pnpm build
```

Then verify `/dashboard` in the in-app browser.

Expected:

- Role tabs still switch data.
- Project selector still drives data fetch.
- Drill-down modal still opens from the cost bar chart.
- No text overlaps at 1280px width.

## Task 5: Contract Ledger First-Slice Refresh

**Files:**

- Modify: `frontend-admin/src/pages/contract/ContractLedgerPage.vue`

- [ ] **Step 1: Keep data and interaction code unchanged**

Do not change these functions except for class names or visual-only wiring:

- `fetchData`
- `fetchKpi`
- `handleSearch`
- `handleReset`
- `handlePageChange`
- `handlePageSizeChange`
- `handleCreate`
- `toggleCol`

- [ ] **Step 2: Update page header**

Use this header pattern at the top of the template:

```vue
<div class="cl-page app-page">
  <div class="cl-page-head">
    <div>
      <a-breadcrumb class="cl-breadcrumb">
        <a-breadcrumb-item>合同管理</a-breadcrumb-item>
        <a-breadcrumb-item>合同台账</a-breadcrumb-item>
      </a-breadcrumb>
      <h1 class="app-page-title">合同台账</h1>
    </div>
  </div>
```

- [ ] **Step 3: Convert filter card to compact filter surface**

Keep existing fields and actions. Style `.cl-filter` as:

```css
.cl-filter {
  padding: 16px;
  margin-bottom: 12px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}
```

Keep `.cl-filter-row` wrapping, but reduce gaps:

```css
.cl-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 18px;
  align-items: center;
  margin-bottom: 12px;
}
```

- [ ] **Step 4: Refresh contract KPI strip**

Keep existing KPI data. Update `.cl-kpis` and `.cl-kpi`:

```css
.cl-kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(150px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.cl-kpi {
  min-height: 88px;
  padding: 15px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  display: flex;
  gap: 12px;
  align-items: flex-start;
  box-shadow: var(--shadow-soft);
}
```

- [ ] **Step 5: Refresh table and toolbar surfaces**

Use the existing toolbar actions. Update table wrapper:

```css
.cl-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.cl-table-wrap {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-soft);
}
```

Add VXE polish:

```css
.cl-table-wrap :deep(.vxe-table--header-wrapper) {
  background: var(--surface-subtle);
}

.cl-table-wrap :deep(.vxe-header--column) {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.cl-table-wrap :deep(.vxe-body--column) {
  color: var(--text);
  font-size: 13px;
}
```

- [ ] **Step 6: Refresh analysis rail**

Keep existing right-side panel content, but use the shared surface feel:

```css
.cl-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cl-panel {
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}
```

- [ ] **Step 7: Add responsive contract ledger behavior**

Use:

```css
@media (max-width: 1280px) {
  .cl-grid {
    grid-template-columns: 1fr;
  }

  .cl-side {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .cl-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cl-side {
    grid-template-columns: 1fr;
  }
}
```

- [ ] **Step 8: Verify contract ledger behavior**

Run:

```powershell
cd frontend-admin
pnpm build
```

Then verify `/contract/ledger` in the in-app browser.

Expected:

- Filters still search and reset.
- KPI endpoint still loads.
- `新建合同` still navigates to `/contract/create`.
- Table, right rail, and pagination fit without overlap.
- Narrow width moves the right rail below the table.

## Task 6: Focused Visual And Regression Verification

**Files:**

- Modify only if tests need adjustment:
  - `frontend-admin/src/layouts/components/__tests__/SidebarMenu.test.ts`

- [ ] **Step 1: Run focused unit tests**

Run:

```powershell
cd frontend-admin
pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts
```

Expected: all tests pass.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
cd frontend-admin
pnpm build
```

Expected: build succeeds. Existing Vite chunk-size warning is acceptable.

- [ ] **Step 3: Verify in the in-app browser**

Open:

```text
http://localhost/dashboard
http://localhost/contract/ledger
```

Expected:

- No Vue 404 page.
- No visible `系统异常，请稍后重试`.
- Sidebar order and `目标管理` rename are visible.
- App shell is light.
- Dashboard and contract ledger visually align with the approved v2 concepts.

- [ ] **Step 4: Capture screenshots for fidelity review**

Capture current browser screenshots for:

- Dashboard desktop.
- Contract ledger desktop.
- Contract ledger around 1280px or narrower.

Compare against:

- `concept-app-shell-dashboard-v2.png`
- `concept-contract-ledger-v2.png`

Record at least five comparison points:

1. Sidebar order and active states.
2. Light shell, topbar, and content background.
3. KPI card density and typography.
4. Contract filter/table/right-rail layout.
5. Responsive behavior and absence of text overlap.

- [ ] **Step 5: Write implementation report**

Write:

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-new-ui-redesign-implementation-result.md`

Include:

- Status.
- Changed files.
- Visual summary.
- Verification commands and exit codes.
- Browser routes checked.
- Known risks.

## Multi-Agent Execution Mapping

Use the local project three-agent workflow for execution:

- Main agent owns orchestration and task state.
- Development agent owns implementation using this plan.
- Testing agent owns independent verification after the implementation report.

Development assignment should point to:

`D:\projects-test\cgc-pms\docs\superpowers\plans\2026-06-13-new-ui-redesign-implementation.md`

Testing assignment should point to the implementation report after development completes.

## Plan Self-Review

Spec coverage:

- Visual direction: covered by Tasks 1, 3, 4, and 5.
- Sidebar order and rename: covered by Task 2.
- App shell: covered by Task 3.
- Dashboard: covered by Task 4.
- Contract ledger: covered by Task 5.
- Tests and browser verification: covered by Task 6.
- Three-agent workflow: covered by Multi-Agent Execution Mapping.

No placeholders are intentionally left in this plan. Exact implementation may still require reading local file context before patching to avoid overwriting unrelated edits.
