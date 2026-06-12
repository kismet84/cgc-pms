# Task 25: Project Overview Page — Evidence

## Files Changed
| File | Action |
|------|--------|
| `frontend-admin/src/types/project.ts` | Added `ProjectOverviewVO` + `MemberBriefVO` types |
| `frontend-admin/src/api/modules/project.ts` | Added `getProjectOverview()` API function |
| `frontend-admin/src/pages/project/overview.vue` | **New** — Overview page |
| `frontend-admin/src/router/index.ts` | Added `/project/:projectId/overview` route |

## Verification
- `pnpm type-check` — **PASS** (zero errors)
- LSP diagnostics — **CLEAN** on all changed files

## Page Features
1. **KPI Cards** (4-column grid, matching dashboard pattern):
   - 合同总额 (contract amount), 动态成本 (dynamic cost), 已付金额 (paid amount), 预警数量 (warning count)
   - All amounts formatted via `toLocaleString('zh-CN')` — displayed from backend, no frontend calculation
2. **ECharts pie chart**: Cost breakdown (paid vs unpaid vs dynamic cost) with donut style
3. **Members table**: Columns for userName, userId, roleCode — with role label mapping
4. **Summary row**: Contract count, member count, monthly warnings, unpaid amount

## API Contract
- `GET /projects/{projectId}/overview` → `ProjectOverviewVO` (T22 backend)
- All monetary values rendered as-is from backend response
