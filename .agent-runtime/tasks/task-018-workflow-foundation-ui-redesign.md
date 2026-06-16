# task-018-workflow-foundation-ui-redesign

## Title

Redesign Approval, Alert, Organization, Basic Data, and System Settings pages using the approved new UI language.

## Status

Completed. (settings/index.vue: minimal form page, compact panel treatment per task scope)

## Main Agent Session

019ecee5-dca1-7c12-bc7c-cde625ce9b05

## User Request

User asked to create 	ask-018 based on docs/superpowers/specs/2026-06-16-ui-page-code-templates.md. This is the final rollout batch covering: Approval (审批管理), Alert (预警中心), Organization (组织架构), Basic Data (基础数据), System Settings (系统设置), and Settings (设置).

## Goal

Update all pages in these foundation modules to match the approved 清爽企业级工作台 UI language. Use Ledger List Page, Detail Page, and Tree And Table Management Page templates as appropriate. System pages keep compact density without heavy charts.

## Design Inputs

- D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-13-new-ui-redesign-design.md
- D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-16-ui-page-code-templates.md
  - Template 1: Ledger List Page
  - Template 3: Tree And Table Management Page
  - Template 4: Detail Page
- Previous completed pages as reference (task-014 ~ 017)

## Target Routes And Files

### 审批管理 (Approval) — Ledger List + Detail
- /approval/todo — 待办
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\approval\todo.vue
- /approval/:instanceId — 审批详情 (hidden route)
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\approval\detail.vue

### 预警中心 (Alert) — Ledger List
- /alert — 预警中心
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\alert\index.vue

### 组织架构 (Org) — Tree And Table
- /org — 组织架构
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\org\index.vue

### 基础数据 (Material)
- /material/dictionary — 材料字典
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\material\dictionary.vue

### 系统设置 (System) — Compact panels
- /system/dict — 字典管理
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\system\dict\index.vue
- /system/users — 用户管理
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\system\users\index.vue
- /system/data — 数据管理
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\system\data\index.vue
- /system/roles — 角色管理
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\system\roles\index.vue

### 设置 (Settings)
- /settings — 设置
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\settings\index.vue

## Scope

### 审批待办 (pproval/todo.vue) — Ledger List Page
- PageHeader: breadcrumb ['审批管理', '待办'], title 待办审批
- Tab switcher: 待办 / 已办 / 我发起 / 抄送我
- KpiStrip: 待办数量, 超时审批, 今日新增, 已处理
- FilterSurface: type, status, keyword
- DataPanel: main table with workflow actions

### 审批详情 (pproval/detail.vue) — Detail Page
- PageHeader: breadcrumb ['审批管理', '审批详情'], title 审批详情
- Detail panels with workflow timeline and approval actions

### 预警中心 (lert/index.vue) — Ledger List Page
- PageHeader: breadcrumb ['预警中心'], title 预警中心
- FilterSurface: level, module, status, date range
- KpiStrip: 高风险, 中风险, 低风险, 已处理
- DataPanel: main table
- AnalysisRail: 风险等级分布, 模块分布, 未处理排行

### 组织架构 (org/index.vue) — Tree And Table
- PageHeader: breadcrumb ['组织架构'], title 组织架构
- Left tree panel + right detail table
- Drawer-based editing

### 基础数据 (material/dictionary.vue) — Tree And Table
- PageHeader: breadcrumb ['基础数据', '材料字典'], title 材料字典
- Left tree panel + right table
- Compact form editing

### 系统设置 (dict/users/data/roles) — Compact panels
- PageHeader with breadcrumb and title on each page
- Compact tables with drawer-based forms
- No heavy charts. Prioritize stable navigation, clear switches, forms
- Permission grouping on roles page

### 设置 (settings/index.vue) — Compact panel
- PageHeader: breadcrumb ['设置'], title 设置
- Compact setting sections

## Constraints

- Do not change backend API signatures or behavior.
- Do not change route paths or route meta.
- Keep existing Pinia store usage intact.
- Do not remove existing business logic (approval actions, tree operations, CRUD forms).
- Follow the 清爽企业级工作台 shared UI language.
- System pages: compact density, no heavy charts, no KPI strip unless naturally fitting.

## Acceptance Criteria

- All 10 pages render without console errors at 1440x900
- pnpm build succeeds
- pnpm vitest run passes (no new test failures)
- Approval workflow (submit, approve, reject) intact
- Tree+table layouts functional on org and material pages
- System CRUD operations (dict, users, roles, data) intact
- No old-style styling patterns remain

## Verification

`powershell
cd frontend-admin
pnpm build
pnpm vitest run
`

## Notes

- This is the final UI rollout batch. All previous tasks (014-017) are completed/under testing.
- Approval detail page is hidden route; keep structure clean and workflow-oriented.
- System pages should feel like clean enterprise settings panels, not dashboards.
