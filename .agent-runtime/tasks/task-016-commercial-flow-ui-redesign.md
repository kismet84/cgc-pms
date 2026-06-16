# task-016-commercial-flow-ui-redesign

## Title

Redesign Variation, Settlement, and Payment pages using the approved new UI language.

## Status

Completed.

## Rework 1 (2026-06-16)

**Issue**: ariation/order.vue 丢失了 handleSubmitApproval 审批提交功能和"提交审批"按钮，导致 5 个测试失败。

**Root cause**: 模板重写时误删了审批提交逻辑。submitVarOrderForApproval import 变成 dead code。

**Fix required**:
1. 恢复 handleSubmitApproval(record) 函数：Modal.confirm → submitVarOrderForApproval(record.id) → etchData()
2. 恢复操作列的"提交审批"按钮：-if="record.approvalStatus === 'DRAFT'"，用新 pt-* 样式
3. 保持所有 pt-* 新 UI 样式，仅恢复丢失的业务逻辑

**Test report**: D:\projects-test\cgc-pms\.agent-runtime\reports\task-016-commercial-flow-ui-redesign-test-report.md

After fix, update the implementation report and notify main agent.

## Main Agent Session

019ecee5-dca1-7c12-bc7c-cde625ce9b05

## User Request

User asked to create 	ask-016 based on docs/superpowers/specs/2026-06-16-ui-page-code-templates.md. This task covers the third rollout batch: Variation & Visa (变更签证), Settlement Management (结算管理), and Payment Management (付款管理).

## Goal

Update the variation/visa list, settlement list/detail, and payment application pages to match the approved 清爽企业级工作台 UI language. Use Ledger List Page and Detail Page templates, improving visual consistency, density, KPI rhythm, table readability, and responsive behavior without changing backend behavior.

## Design Inputs

- D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-13-new-ui-redesign-design.md
- D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-16-ui-page-code-templates.md
  - Template 1: Ledger List Page
  - Template 4: Detail Page
- Previous reference pages:
  - D:\projects-test\cgc-pms\.agent-runtime\reports\task-011-screenshots\dashboard-desktop-1440x900-verified.png
  - D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-contract-ledger-v2.png

## Target Routes And Files

### 变更签证 (Variation & Visa)
- /variation/order — 变更签证
- D:\projects-test\cgc-pms\frontend-admin\src\pages\variation\order.vue

### 结算管理 (Settlement)
- /settlement/list — 结算列表
- /settlement/:id — 结算详情 (hidden route)
- D:\projects-test\cgc-pms\frontend-admin\src\pages\settlement\index.vue
- D:\projects-test\cgc-pms\frontend-admin\src\pages\settlement\detail.vue

### 付款管理 (Payment)
- /payment/application — 付款申请
- D:\projects-test\cgc-pms\frontend-admin\src\pages\payment\index.vue

## Scope

### 变更签证 (ariation/order.vue) — Ledger List Page

- PageHeader: breadcrumb ['变更签证'], title 变更签证
- FilterSurface: compact inline filter (project, status, type, keyword, date range)
- KpiStrip: 变更总额, 已审批金额, 待审批数量, 影响利润
- DataPanel: main table (编号, 名称, 类型, 金额, 状态, 日期, 操作)
- AnalysisRail (right): 变更类型分布, 审批状态统计, 金额 Top5

### 结算列表 (settlement/index.vue) — Ledger List Page

- PageHeader: breadcrumb ['结算管理', '结算列表'], title 结算列表
- FilterSurface: compact inline filter (project, status, keyword, date range)
- KpiStrip: 累计结算金额, 待审核金额, 已确认金额, 结算进度
- DataPanel: main table (编号, 项目, 金额, 状态, 日期, 操作)
- AnalysisRail (right): 结算状态分布, 月度结算趋势, 异常结算提醒

### 结算详情 (settlement/detail.vue) — Detail Page

- PageHeader: breadcrumb ['结算管理', '结算详情'], title 结算详情
- Detail layout: top info card + detail panels
- Action toolbar: approve, reject, export

### 付款申请 (payment/index.vue) — Ledger List Page

- PageHeader: breadcrumb ['付款管理', '付款申请'], title 付款申请
- FilterSurface: compact inline filter (project, status, keyword, date range)
- KpiStrip: 待付款金额, 已审批未支付, 今日应付, 超比例付款
- DataPanel: main table (编号, 项目, 金额, 状态, 日期, 操作)
- AnalysisRail (right): 付款状态统计, 资金风险, 临期付款

## Constraints

- Do not change backend API signatures or behavior.
- Do not change route paths or route meta.
- Keep existing Pinia store usage intact.
- Do not remove existing data-fetching logic; only restyle the presentation layer.
- Follow the 清爽企业级工作台 shared UI language:
  - light gray page background; white content surfaces; compact enterprise density;
  - subtle borders; 6-8px radius; blue primary actions;
  - semantic red, orange, green, blue only when meaningful;
  - no marketing hero, decorative gradient blobs, nested cards, or oversized display type.

## Acceptance Criteria

- All 4 pages render without console errors at 1440x900
- KPI strips show real/simulated data correctly
- Filter surfaces and data tables functional
- Analysis rails present on list pages
- Detail page has proper layout structure
- No horizontal overflow at 937x900
- No old-style styling patterns remain
- pnpm build succeeds

## Verification

`powershell
cd frontend-admin
pnpm build
`

Browser verification at 1440x900, 937x900, 390x844:

- /variation/order
- /settlement/list
- /payment/application

## Notes

- task-014 (project + target) and task-015 (cost) are already completed and serve as UI reference.
- Settlement detail page is a hidden route; keep its structure clean but lighter than list pages.
