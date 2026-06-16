# task-015-cost-ui-redesign

## Title

Redesign Cost Management pages using the approved new UI language.

## Status

Completed.

## User Request

User asked to create 	task-015 based on docs/superpowers/specs/2026-06-16-ui-page-code-templates.md. This task covers the second rollout batch: Cost Management (成本管理).

## Goal

Update the cost ledger and cost summary pages so they match the approved 清爽企业级工作台 UI language already applied to the dashboard, contract ledger, and project/target pages.

The implementation should use the Analysis Dashboard Page template plus Ledger List Page template as appropriate, improving visual consistency, density, KPI rhythm, chart readability, and responsive behavior without changing backend behavior.

## Design Inputs

- D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-13-new-ui-redesign-design.md
- D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-16-ui-page-code-templates.md
  - Template 1: Ledger List Page (for 成本台账)
  - Template 2: Analysis Dashboard Page (for 动态成本汇总)
- Dashboard reference:
  - D:\projects-test\cgc-pms\.agent-runtime\reports\task-011-screenshots\dashboard-desktop-1440x900-verified.png
- Contract ledger reference:
  - D:\projects-test\cgc-pms\.agent-runtime\reports\task-010-assets\concept-contract-ledger-v2.png

## Target Routes And Files

Cost Management routes:

- /cost/ledger — 成本台账
- /cost/summary — 动态成本汇总

Cost files:

- D:\projects-test\cgc-pms\frontend-admin\src\pages\cost\ledger.vue
- D:\projects-test\cgc-pms\frontend-admin\src\pages\cost\summary.vue
- D:\projects-test\cgc-pms\frontend-admin\src\api\modules\cost.ts
- D:\projects-test\cgc-pms\frontend-admin\src\types\cost.ts

Note: cost-target routes were covered in task-014 and are out of scope.

## Scope

### 成本台账 (cost/ledger.vue) — Ledger List Page

- PageHeader: breadcrumb ['成本管理', '成本台账'], title 成本台账
- FilterSurface: compact inline filter form (project, subject, source type, keyword, date range)
- KpiStrip: 成本总额, 锁定成本, 动态成本, 偏差金额
- DataPanel: main cost ledger table with columns (编号, 成本科目, 来源类型, 金额, 日期, 操作)
- AnalysisRail (right side): 成本科目占比, 来源类型分布, 超预算预警
- Keep existing API calls (getCostLedger, getCostLedgerSummary, getCostLedgerDetail, getCostSubjectTree) intact

### 动态成本汇总 (cost/summary.vue) — Analysis Dashboard Page

- PageHeader: breadcrumb ['成本管理', '动态成本汇总'], title 动态成本汇总
- Top KPI row: 目标成本, 锁定成本, 动态成本, 偏差金额, 偏差率
- Panels: 成本执行概览, 成本构成分析, 偏差趋势分析, 超预算预警, 成本科目排行, 异常明细
- Use ECharts via ue-echarts for charts, consistent with existing dashboard
- Keep existing API calls (getCostSummary, efreshCostSummary) intact

## Constraints

- Do not change backend API signatures or behavior.
- Do not change route paths or route meta.
- Keep existing Pinia store usage intact.
- Do not remove existing data-fetching logic; only restyle the presentation layer.
- Follow the 清爽企业级工作台 shared UI language:
  - light gray page background;
  - white content surfaces;
  - compact enterprise density;
  - subtle borders;
  - 6-8px radius;
  - blue primary actions;
  - semantic red, orange, green, blue only when meaningful;
  - no marketing hero, decorative gradient blobs, nested cards, or oversized display type.

## Acceptance Criteria

- /cost/ledger renders without console errors at 1440x900
- /cost/summary renders without console errors at 1440x900
- Both pages show proper KPI strips with real data
- Ledger page shows filter surface and data table
- Summary page shows analysis dashboard panels with charts
- No horizontal overflow at 937x900
- No old-style styling patterns remain (nested cards, hero sections, gradient blobs)
- pnpm build succeeds

## Verification

`powershell
cd frontend-admin
pnpm build
`

Browser verification at:

- 1440x900
- 937x900
- 390x844

Checklist:

- required page titles present;
- required KPI labels present;
- required analysis panel labels present;
- no old labels reintroduced;
- no console error;
- no blank page;
- primary table/filter/actions visible.

## Notes

- This task is the second batch of the UI rollout plan. task-014 (project + target) is complete.
- cost-target (目标管理 under 成本) was already handled in task-014; stay within 成本台账 + 动态成本汇总 scope.
