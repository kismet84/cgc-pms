# task-009-kpi-route-conflict

## Title

Fix KPI route conflicts found by in-app browser smoke testing.

## Status

Completed.

## User Request

The user asked whether the three-agent collaboration mechanism is being used. This task starts the collaborative workflow for the two page errors found during the in-app browser smoke test.

## Goal

Fix the backend route conflicts that make KPI endpoints return HTTP 500:

- `GET /api/contracts/kpi`
- `GET /api/settlements/kpi`

Both endpoints currently appear to be captured by generic `/{id}` mappings, causing Spring to attempt converting the string `kpi` to a `Long`.

## Scope

- Inspect backend contract and settlement controllers/routes.
- Ensure static KPI routes are matched by their intended controller methods.
- Preserve existing API behavior for real numeric id routes.
- Add or update focused backend tests covering both KPI endpoints.
- Run focused backend verification.

## Out of Scope

- Do not change unrelated frontend behavior.
- Do not refactor unrelated controller/service code.
- Do not address notification SSE log noise unless it is required for this task.

## Constraints

- Follow existing project style.
- Keep changes narrowly scoped.
- Do not revert unrelated uncommitted changes.
- Child agents must not dispatch other agents.
- Communication back to the main agent must be only a report document path plus one short sentence.

## Evidence From Browser Smoke Test

Report:

`D:\projects-test\cgc-pms\.agent-runtime\tasks\task-008-browser-page-smoke-test.md`

Failing pages:

| Page | Endpoint | Symptom |
| --- | --- | --- |
| `http://localhost/contract/ledger` | `GET /api/contracts/kpi` | HTTP 500, visible message `系统异常，请稍后重试`, `加载合同指标失败，请稍后重试` |
| `http://localhost/settlement/list` | `GET /api/settlements/kpi` | HTTP 500, visible message `系统异常，请稍后重试` |

Backend root error for both endpoints:

- `MethodArgumentTypeMismatchException`
- Parameter `id` failed to convert `kpi` to `Long`
- `NumberFormatException: For input string: "kpi"`

## Acceptance Criteria

- `GET /api/contracts/kpi` no longer returns 500 from `kpi` being parsed as `Long id`.
- `GET /api/settlements/kpi` no longer returns 500 from `kpi` being parsed as `Long id`.
- Numeric id routes for contracts and settlements still work as before.
- Focused backend tests cover the KPI routes.
- Implementation report is written to:
  - `D:\projects-test\cgc-pms\.agent-runtime\reports\task-009-kpi-route-conflict-implementation-result.md`

## Suggested Verification

Run focused backend tests related to contract and settlement controllers/services. If exact test names differ, choose the narrowest matching tests and document the command.

Suggested commands:

```powershell
cd D:\projects-test\cgc-pms\backend
.\mvnw.cmd -q test-compile
.\mvnw.cmd -q "-Dtest=*Contract*Test,*Settlement*Test" test
```

If wildcard test selection does not match the local test layout, adjust to the specific tests added or updated and record the command.

## Rework History

None.

## Implementation Report

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-009-kpi-route-conflict-implementation-result.md`

Development agent reported completion on 2026-06-13. The implementation added static KPI routes for contracts and settlements, added focused service aggregation methods, and added `ContractSettlementKpiRouteTest`.

## Test Report

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-009-kpi-route-conflict-test-report.md`

Testing agent reported `pass` on 2026-06-13. Focused KPI route regression, backend test compilation, and contract/settlement regression selection all passed.
