# task-017-execution-support-ui-redesign

## Title

Redesign Subcontract, Procurement, Inventory, and Invoice pages using the approved new UI language.

## Status

Completed.

## Rework 1 (2026-06-16)

**Issue**: 8 个页面仅完成了页头+筛选+表格的 pt-* 迁移，KPI strip 和 AnalysisRail 全部缺失，与任务文档要求不符。

**Missing per page**:

| Page | Missing KPI | Missing Rail |
|------|------------|-------------|
| subcontract/task | 分包合同额/已计量/已付款/待结算 | 分包类型分布/单位排行/异常提醒 |
| subcontract/measure | 计量总额/已审核/待审核 | — (optional) |
| purchase/order | 采购申请数/待审批/已下单金额/未入库金额 | 采购状态/供应商排行/异常采购 |
| inventory/warehouse | 库存总值/物料种类 | — |
| inventory/stock | 库存总值/低库存物料/近期待入库/近期待出库 | 物料类别分布/低库存预警/库存金额 Top5 |
| inventory/transaction | 入库总额/出库总额 | — |
| inventory/purchase-request | 申请数/待审批 | — |
| invoice/index | 发票总额/已开票/未开票/异常发票 | 发票状态分布/税率分布/异常提醒 |

**Fix required**:
1. Add KPI strip (pt-kpi-strip) on all 7 pages that require it
2. Add AnalysisRail on 5 primary pages (subcontract/task, purchase/order, inventory/stock, invoice/index, subcontract/measure optional)
3. KPI values can be front-end computed from table data or mock placeholders where API unavailable
4. AnalysisRail charts use ECharts via vue-echarts or compact text lists
5. Retain all existing pt-* styling

**Test report**: D:\projects-test\cgc-pms\.agent-runtime\reports\task-017-execution-support-ui-redesign-test-report.md

After fix, update the implementation report and notify main agent.

## Main Agent Session

019ecee5-dca1-7c12-bc7c-cde625ce9b05

## User Request

User asked to create 	ask-017 based on docs/superpowers/specs/2026-06-16-ui-page-code-templates.md. This task covers the fourth rollout batch: Subcontract (分包管理), Procurement (采购管理), Inventory (库存管理), and Invoice (发票管理).

## Goal

Update all pages in these four modules to match the approved 清爽企业级工作台 UI language. Use Ledger List Page as the primary template, improving visual consistency, density, KPI rhythm, table readability, and responsive behavior without changing backend behavior.

## Design Inputs

- D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-13-new-ui-redesign-design.md
- D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-16-ui-page-code-templates.md
  - Template 1: Ledger List Page
- Previous completed pages as reference:
  - task-014: project + target
  - task-015: cost ledger + summary
  - task-016: variation + settlement + payment

## Target Routes And Files

### 分包管理 (Subcontract)
- /subcontract/task — 分包任务
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\subcontract\task.vue
- /subcontract/measure — 分包计量
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\subcontract\measure.vue

### 采购管理 (Procurement)
- /purchase/order — 采购订单
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\purchase\order.vue

### 库存管理 (Inventory)
- /inventory/warehouse — 仓库
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\inventory\warehouse.vue
- /inventory/stock — 库存台账
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\inventory\stock.vue
- /inventory/transaction — 库存交易
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\inventory\transaction.vue
- /inventory/purchase-request — 采购申请
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\inventory\purchase-request.vue

### 发票管理 (Invoice)
- /invoice — 发票管理
  - D:\projects-test\cgc-pms\frontend-admin\src\pages\invoice\index.vue

## Scope

### 分包任务 (subcontract/task.vue) — Ledger List Page
- PageHeader: breadcrumb ['分包管理', '分包任务'], title 分包任务
- FilterSurface: project, status, keyword
- KpiStrip: 分包合同额, 已计量, 已付款, 待结算
- DataPanel: main table
- AnalysisRail: 分包类型分布, 单位排行, 异常提醒

### 分包计量 (subcontract/measure.vue) — Ledger List Page
- PageHeader: breadcrumb ['分包管理', '分包计量'], title 分包计量
- FilterSurface: project, subcontract, status, date range
- KpiStrip: 计量总额, 已审核, 待审核
- DataPanel: main table
- Simpler AnalysisRail as appropriate

### 采购订单 (purchase/order.vue) — Ledger List Page
- PageHeader: breadcrumb ['采购管理', '采购订单'], title 采购订单
- FilterSurface: project, supplier, status, keyword
- KpiStrip: 采购申请数, 待审批, 已下单金额, 未入库金额
- DataPanel: main table
- AnalysisRail: 采购状态, 供应商排行, 异常采购

### 仓库 (inventory/warehouse.vue) — Ledger List Page
- PageHeader: breadcrumb ['库存管理', '仓库'], title 仓库
- FilterSurface: project, keyword
- KpiStrip: 库存总值, 物料种类
- DataPanel: main table

### 库存台账 (inventory/stock.vue) — Ledger List Page
- PageHeader: breadcrumb ['库存管理', '库存台账'], title 库存台账
- FilterSurface: warehouse, material, keyword
- KpiStrip: 库存总值, 低库存物料, 近期待入库, 近期待出库
- DataPanel: main table
- AnalysisRail: 物料类别分布, 低库存预警, 库存金额 Top5

### 库存交易 (inventory/transaction.vue) — Ledger List Page
- PageHeader: breadcrumb ['库存管理', '库存交易'], title 库存交易
- FilterSurface: warehouse, type, date range, keyword
- KpiStrip: 入库总额, 出库总额
- DataPanel: main table

### 采购申请 (inventory/purchase-request.vue) — Ledger List Page
- PageHeader: breadcrumb ['库存管理', '采购申请'], title 采购申请
- FilterSurface: project, status, keyword
- KpiStrip: 申请数, 待审批
- DataPanel: main table

### 发票管理 (invoice/index.vue) — Ledger List Page
- PageHeader: breadcrumb ['发票管理'], title 发票管理
- FilterSurface: project, contract, type, status, date range
- KpiStrip: 发票总额, 已开票, 未开票, 异常发票
- DataPanel: main table
- AnalysisRail: 发票状态分布, 税率分布, 异常提醒

## Constraints

- Do not change backend API signatures or behavior.
- Do not change route paths or route meta.
- Keep existing Pinia store usage intact.
- Do not remove existing data-fetching logic; only restyle the presentation layer.
- Follow the 清爽企业级工作台 shared UI language.
- Do not remove existing business functions like submit, approve, create, edit, delete.

## Acceptance Criteria

- All 8 pages render without console errors at 1440x900
- pnpm build succeeds
- pnpm vitest run passes (no new test failures)
- KPI strips, filter surfaces, data tables present on all pages
- Analysis rails present on primary list pages
- No old-style styling patterns remain
- No business logic removed

## Verification

`powershell
cd frontend-admin
pnpm build
pnpm vitest run
`

## Notes

- Inventory sub-pages (warehouse, transaction, purchase-request) may be simpler; focus on header + filter + table, KPI/AnalysisRail optional.
- Primary pages (subcontract/task, purchase/order, inventory/stock, invoice/index) get full Ledger List treatment.
