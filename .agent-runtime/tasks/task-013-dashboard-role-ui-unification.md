# task-013-dashboard-role-ui-unification

## Title

Unify all dashboard role views under the project-manager dashboard UI language.

## Status

Completed after Rework 1.

## User Request

User approved visual option A: keep the same dashboard skeleton and visual language as the improved `项目总` page, while preserving role-specific business content for the other dashboard tabs.

## Goal

Update `frontend-admin/src/pages/dashboard/index.vue` so these role tabs use the same new UI language and layout rhythm as `项目总`:

- `商务经理`
- `成本经理`
- `财务`
- `管理层`

The result should feel like one cohesive dashboard product, not one redesigned role plus four legacy role panels.

## Approved Direction

Use option A from the visual companion:

> Same skeleton and UI language, role-specific business content.

Do not make all roles identical. Keep each role's business focus:

- `商务经理`: contract amount, contract changes, variation orders, settlement progress, payment ratio, recent changes, settlement items.
- `成本经理`: target cost, dynamic cost, cost deviation, expected profit, actual cost, cost composition, over-budget alerts.
- `财务`: pending payments, approved-unpaid, over-ratio payments, warranty expiration, payment plans, payment exceptions.
- `管理层`: cross-project totals, active projects, total contract amount, dynamic cost, expected profit, total paid, risk/pending counts, project rankings, major risks, overdue items.

## Context

Project redesign spec:

- `D:\projects-test\cgc-pms\docs\superpowers\specs\2026-06-13-new-ui-redesign-design.md`

Reference dashboard implementation and reports:

- `D:\projects-test\cgc-pms\.agent-runtime\tasks\task-011-dashboard-reference-fidelity.md`
- `D:\projects-test\cgc-pms\.agent-runtime\reports\task-011-dashboard-reference-fidelity-implementation-result.md`
- `D:\projects-test\cgc-pms\.agent-runtime\reports\task-011-screenshots\dashboard-desktop-1440x900-verified.png`

Current target file:

- `D:\projects-test\cgc-pms\frontend-admin\src\pages\dashboard\index.vue`

The current `项目总` role already has the desired new UI rhythm:

1. compact header and role tabs;
2. KPI strip;
3. middle three-panel analytics grid;
4. bottom three-column compact table grid;
5. responsive stacking and no horizontal overflow.

Other roles still use older combinations such as `kpi-grid-6`, `chart-row`, and ad hoc panels.

## Scope

Allowed primary implementation:

- `D:\projects-test\cgc-pms\frontend-admin\src\pages\dashboard\index.vue`

Allowed focused tests:

- `D:\projects-test\cgc-pms\frontend-admin\src\pages\dashboard\__tests__\DashboardRoleUnification.test.ts`
- Existing dashboard/reference tests may be updated only if necessary.

Allowed shared CSS only if strictly needed:

- `D:\projects-test\cgc-pms\frontend-admin\src\assets\styles\global.css`

Do not change backend APIs, stores, permissions, routing, or unrelated modules.

Do not revert unrelated working-tree changes.

## Implementation Plan

### Task 1: Add a failing source-level dashboard role unification test

Create:

- `frontend-admin/src/pages/dashboard/__tests__/DashboardRoleUnification.test.ts`

Test requirements:

- Read `frontend-admin/src/pages/dashboard/index.vue` as source text.
- Assert all role labels remain present:
  - `项目总`
  - `商务经理`
  - `成本经理`
  - `财务`
  - `管理层`
- Assert the shared dashboard layout classes are used for non-`pm` roles:
  - `role-dashboard-grid`
  - `role-metric-strip`
  - `role-analysis-grid`
  - `role-table-grid`
- Assert role-specific section labels are present:
  - Business manager: `合同经营概览`, `变更签证分析`, `结算收付概览`
  - Cost manager: `成本执行概览`, `成本构成分析`, `偏差趋势分析`
  - Finance: `资金支付概览`, `付款结构分析`, `资金风险概览`
  - Management: `项目经营总览`, `项目风险分布`, `经营趋势概览`
- Assert the legacy-only layout pattern is not dominant for those roles. It is acceptable for existing CSS names to remain temporarily, but non-PM role templates should not rely only on `chart-row` as their primary structure.

Run:

```powershell
cd frontend-admin
pnpm vitest run src/pages/dashboard/__tests__/DashboardRoleUnification.test.ts --configLoader runner
```

Expected before implementation: fail because the new shared classes and role-specific section labels are absent.

### Task 2: Extract or introduce shared dashboard presentation helpers

Modify:

- `frontend-admin/src/pages/dashboard/index.vue`

Add local presentation helpers near the existing PM reference helpers:

- role-specific fallback rows for empty tables;
- role-specific computed chart options using ECharts;
- compact computed data arrays for each role's three-panel middle region;
- shared summary helpers where practical.

Keep helpers local to the dashboard file for this task unless extraction is clearly necessary. Avoid a broad refactor.

### Task 3: Redesign `商务经理` role view

Modify the `activeRole === 'bm'` template so it follows the PM-like skeleton:

1. KPI strip:
   - `合同总额`
   - `合同变更`
   - `签证变更`
   - `结算进度`
2. Middle three-panel region:
   - `合同经营概览`: summary metrics plus bar/line chart.
   - `变更签证分析`: doughnut or grouped chart for contract changes and variation orders.
   - `结算收付概览`: settlement progress/payment ratio panel with trend chart or compact status list.
3. Bottom three-column table region:
   - `近期合同变更`
   - `待结算事项`
   - `收付款关注`

Use existing `bmData` fields first. If arrays are empty, use clearly local presentation fallback rows only for visual density.

### Task 4: Redesign `成本经理` role view

Modify the `activeRole === 'cost'` template so it follows the PM-like skeleton:

1. KPI strip:
   - `目标成本`
   - `动态成本`
   - `成本偏差`
   - `预计利润`
2. Middle three-panel region:
   - `成本执行概览`: target/dynamic/actual comparison.
   - `成本构成分析`: keep or adapt the existing cost subject chart/doughnut.
   - `偏差趋势分析`: deviation/profit trend.
3. Bottom three-column table region:
   - `超预算预警`
   - `成本科目排行`
   - `成本下钻提示` or `成本偏差明细`

Preserve existing `handleBarClick` drill-down behavior if the subject chart remains clickable.

### Task 5: Redesign `财务` role view

Modify the `activeRole === 'finance'` template so it follows the PM-like skeleton:

1. KPI strip:
   - `待付款金额`
   - `待付款笔数`
   - `已审批未支付`
   - `质保金到期`
2. Middle three-panel region:
   - `资金支付概览`
   - `付款结构分析`
   - `资金风险概览`
3. Bottom three-column table region:
   - `待付款明细`
   - `超比例付款`
   - `质保金到期`

Use existing finance arrays where available and quiet presentation fallback rows only for empty visual states.

### Task 6: Redesign `管理层` role view

Modify the `activeRole === 'mgmt'` template so it follows the PM-like skeleton:

1. KPI strip:
   - `在建项目`
   - `合同总额`
   - `动态成本`
   - `风险预警`
2. Middle three-panel region:
   - `项目经营总览`
   - `项目风险分布`
   - `经营趋势概览`
3. Bottom three-column table region:
   - `项目经营排名`
   - `重大风险`
   - `逾期事项（>7天）`

For management role, there is no project selector. Preserve this behavior.

### Task 7: Add shared CSS classes for role dashboard layouts

Modify scoped CSS in `frontend-admin/src/pages/dashboard/index.vue`:

- `.role-dashboard-grid`
- `.role-metric-strip`
- `.role-analysis-grid`
- `.role-table-grid`
- `.role-panel`
- `.role-chart`
- `.role-summary-strip`
- responsive rules matching PM behavior at:
  - desktop `1440x900`;
  - mid width around `937px`;
  - mobile `390x844`.

Prefer reusing existing PM styles where possible. Do not create a visually separate palette for each role.

### Task 8: Verification

Run:

```powershell
cd frontend-admin
pnpm vitest run src/pages/dashboard/__tests__/DashboardRoleUnification.test.ts --configLoader runner
pnpm vitest run src/pages/dashboard/__tests__/DashboardReferenceFidelity.test.ts src/pages/dashboard/__tests__/DashboardRoleUnification.test.ts --configLoader runner
pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts src/layouts/__tests__/BasicLayout.mobile.test.ts src/pages/dashboard/__tests__/DashboardReferenceFidelity.test.ts src/pages/dashboard/__tests__/DashboardRoleUnification.test.ts src/pages/contract/__tests__/ContractLedgerReferenceFidelity.test.ts --configLoader runner
pnpm build
```

Expected final results:

- all listed tests pass;
- production build exits `0`;
- existing large chunk warning may remain.

### Task 9: Browser verification

Use the in-app browser or Playwright against the locally deployed frontend.

Verify `/dashboard` for all five role tabs:

- `项目总`
- `商务经理`
- `成本经理`
- `财务`
- `管理层`

At minimum verify:

- no blank page;
- no framework error overlay;
- no relevant console errors;
- role tab switching works;
- each non-PM role shows:
  - KPI strip;
  - three middle panels;
  - three bottom panels;
  - no horizontal overflow.

Viewports:

- `1440x900`
- around `937x900`
- `390x844`

Save screenshots under:

- `D:\projects-test\cgc-pms\.agent-runtime\reports\task-013-screenshots\`

Suggested screenshot names:

- `dashboard-bm-1440x900.png`
- `dashboard-cost-1440x900.png`
- `dashboard-finance-1440x900.png`
- `dashboard-mgmt-1440x900.png`
- `dashboard-roles-mobile-390x844.png`

## Acceptance Criteria

- Non-PM role dashboards visibly share the same new UI language as `项目总`.
- Each role keeps business-specific labels, metrics, charts, and tables.
- Existing role tabs and project selector behavior continue to work.
- `管理层` remains non-project-scoped.
- Focused role unification test is added and follows red/green evidence.
- Existing task-011 dashboard reference fidelity test still passes.
- Existing task-010 sidebar/mobile and task-012 contract tests still pass.
- `pnpm build` passes.
- Browser verification covers all role tabs and responsive widths.

## Expected Implementation Report

Write:

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-013-dashboard-role-ui-unification-implementation-result.md`

Include:

- status;
- summary;
- changed files;
- red/green TDD evidence;
- verification commands and exit codes;
- browser verification evidence and screenshot paths;
- known risks and intentional deviations.

## Rework History

### Rework 1 - related contract ledger regression

Testing report:

- `D:\projects-test\cgc-pms\.agent-runtime\reports\task-013-dashboard-role-ui-unification-test-report.md`

Testing result:

- Dashboard role UI unification passed focused tests, desktop browser checks, and mobile browser checks.
- Related regression suite failed on `frontend-admin/src/pages/contract/__tests__/ContractLedgerReferenceFidelity.test.ts`.
- Browser verification confirmed the current contract ledger page is missing:
  - `合同类型分布`
  - `合同状态统计`
  - `逾期预警`
  - `.cl-analysis-rail`

Rework scope:

- Fix the contract ledger reference analysis rail regression so the task-012 contract ledger fidelity test passes again.
- Keep task-013 dashboard role changes intact.
- Do not change backend behavior.
- Do not revert unrelated user changes.

Required files to inspect:

- `D:\projects-test\cgc-pms\frontend-admin\src\pages\contract\ContractLedgerPage.vue`
- `D:\projects-test\cgc-pms\frontend-admin\src\pages\contract\__tests__\ContractLedgerReferenceFidelity.test.ts`
- `D:\projects-test\cgc-pms\.agent-runtime\reports\task-012-contract-ledger-reference-fidelity-implementation-result.md`
- `D:\projects-test\cgc-pms\.agent-runtime\reports\task-012-contract-ledger-reference-fidelity-test-report.md`

Required verification:

```powershell
cd frontend-admin
pnpm vitest run src/pages/contract/__tests__/ContractLedgerReferenceFidelity.test.ts --configLoader runner
pnpm vitest run src/pages/dashboard/__tests__/DashboardReferenceFidelity.test.ts src/pages/dashboard/__tests__/DashboardRoleUnification.test.ts src/pages/contract/__tests__/ContractLedgerReferenceFidelity.test.ts --configLoader runner
pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts src/layouts/__tests__/BasicLayout.mobile.test.ts src/pages/dashboard/__tests__/DashboardReferenceFidelity.test.ts src/pages/dashboard/__tests__/DashboardRoleUnification.test.ts src/pages/contract/__tests__/ContractLedgerReferenceFidelity.test.ts --configLoader runner
pnpm build
```

Implementation report update:

- Update `D:\projects-test\cgc-pms\.agent-runtime\reports\task-013-dashboard-role-ui-unification-implementation-result.md`.
- Include Rework 1 summary, changed files, verification commands, and known risks.
