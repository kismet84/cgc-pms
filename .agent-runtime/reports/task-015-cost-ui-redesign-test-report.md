# task-015-cost-ui-redesign test_report

## Decision

**pass**

## Executed Commands

| # | Command | Exit | Notes |
|---|---------|------|-------|
| 1 | `cd frontend-admin; pnpm build` | 0 | vue-tsc noEmit + vite build passed; new chunks ledger-Dpd9azar.js (12.87 kB) + summary-Yz_rrHd0.js (10.88 kB) |
| 2 | `cd frontend-admin; pnpm vitest run` | 1 (1 fail) | 26/27 files pass (135/136 tests); sole failure is pre-existing in ContractLedgerPage.test.ts, unrelated to cost pages |
| 3 | `git diff --stat HEAD -- src/pages/cost/ledger.vue src/pages/cost/summary.vue` | 0 | 2 files, 778+ / 912- |
| 4 | Route grep (router/index.ts) | — | /cost/ledger → ledger.vue, /cost/summary → summary.vue confirmed |

## Passed Checks

- [x] **pnpm build** succeeds (zero errors, vue-tsc noEmit + vite build)
- [x] **ledger.vue** uses shared UI language (pt-page-head, pt-kpi-strip, pt-filter-surface, pt-ledger-layout, pt-analysis-rail, pt-panel, pt-compact-list)
- [x] **summary.vue** uses shared UI language (pt-page-head, pt-kpi-strip, pt-panel, pt-compact-list) + vue-echarts VChart
- [x] PageHeader present on both pages: breadcrumb 成本管理 / 成本台账 or 动态成本汇总
- [x] Ledger KPI strip: 成本总额, 锁定成本, 动态成本, 偏差金额 (4 KPIs)
- [x] Summary KPI strip: 目标成本, 锁定成本, 动态成本, 偏差金额, 偏差率 (5 KPIs)
- [x] Ledger filter surface: project, contract, partner, sourceType, costSubject (TreeSelect), dateRange, keyword
- [x] Ledger data table present (成本清单) + pagination
- [x] Ledger analysis rail (right): 成本科目占比, 来源类型分布, 超预算预警
- [x] Summary 6 analysis panels (2×3 grid): 成本执行概览 (bar), 成本构成分析 (ring), 偏差趋势分析 (bar), 超预算预警, 成本科目排行 (hbar), 异常明细
- [x] Summary subject detail table (7 columns) retained
- [x] Summary empty state when no project selected
- [x] Old `cl-*` scoped CSS removed; only `.cl-money` (tabular-nums) in ledger.vue and `.pt-chart` (260px height) in summary.vue remain
- [x] No hero sections, gradient blobs, nested cards, or decorative patterns
- [x] Existing API calls intact (getCostLedger, getCostLedgerSummary, getCostLedgerDetail, getCostSummary, refreshCostSummary)
- [x] Router paths unchanged (/cost/ledger, /cost/summary)
- [x] Project-contract linkage filter logic retained in ledger.vue
- [x] Detail drawer retained in ledger.vue
- [x] 135/136 tests pass; the 1 failure is a pre-existing ContractLedgerPage test issue

## Failed Checks

- None attributable to task-015. The single vitest failure (`ContractLedgerPage.test.ts > wires 全部预警 link`) is pre-existing and tests the contract ledger, not the cost management pages.

## Recommendations

- The pre-existing ContractLedgerPage test failure should be addressed in a separate task (expected "全部预警" text in template not found).
- For full visual verification, run the dev server (`pnpm dev`) and browse `/cost/ledger` and `/cost/summary` at 1440×900 and 937×900 to confirm no horizontal overflow.

## Notes

- Implementation scope is correct: only `ledger.vue` and `summary.vue` changed; cost-target pages were not touched (covered by task-014).
- The build warning about large chunks (vendor-antd, vendor-vxe) is pre-existing and unrelated.
- KPI "锁定成本" in ledger defaults to `CT_CONTRACT` source type with fallback to `totalAmount` — acceptable local interpretation.
- "偏差趋势分析" uses subject-level bar chart instead of a time-series line, as API does not provide temporal data — documented deviation in implementation report.
