# task-009-kpi-route-conflict implementation_result

## Routing

- message_id: `msg-012`
- task_id: `task-009-kpi-route-conflict`
- from: `019ebc4c-8e43-70d3-bc4e-8cf25548cd68`
- to: `019ebc4b-a8d8-7a61-9fd3-b6b5e2a172db`
- type: `implementation_result`

## Status

`completed`

## Summary

Fixed the KPI route conflicts for:

- `GET /api/contracts/kpi`
- `GET /api/settlements/kpi`

Root cause: `CtContractController` and `StlSettlementController` had numeric id routes (`/{id}`) but no static `/kpi` routes. Runtime requests to `/contracts/kpi` and `/settlements/kpi` were therefore handled as id lookups and Spring attempted to convert the string `kpi` into a `Long`, causing `MethodArgumentTypeMismatchException` and HTTP 500.

Fix:

- Added static `GET /contracts/kpi` route before `/{id}` in `CtContractController`.
- Added static `GET /settlements/kpi` route before `/{id}` in `StlSettlementController`.
- Added narrowly scoped read-only KPI aggregation methods in `CtContractService` and `StlSettlementService`.
- Added a focused standalone MockMvc route regression test covering both KPI endpoints and their frontend response fields.

## Changed Files

- `D:\projects-test\cgc-pms\backend\src\main\java\com\cgcpms\contract\controller\CtContractController.java`
- `D:\projects-test\cgc-pms\backend\src\main\java\com\cgcpms\contract\service\CtContractService.java`
- `D:\projects-test\cgc-pms\backend\src\main\java\com\cgcpms\settlement\controller\StlSettlementController.java`
- `D:\projects-test\cgc-pms\backend\src\main\java\com\cgcpms\settlement\service\StlSettlementService.java`
- `D:\projects-test\cgc-pms\backend\src\test\java\com\cgcpms\route\ContractSettlementKpiRouteTest.java`

## Verification

### Red Test

- Command: `cd backend && .\mvnw.cmd -q '-Dtest=ContractSettlementKpiRouteTest' test`
- Exit code before production fix: `1`
- Expected failure observed:
  - `CtContractService.getKpi(...)` missing.
  - `StlSettlementService.getKpi(...)` missing.
  - This confirmed the backend had no intended KPI endpoint path and the test was exercising the missing behavior.

### Green Tests

- Command: `cd backend && .\mvnw.cmd -q '-Dtest=ContractSettlementKpiRouteTest' test`
- Exit code: `0`
- Result: `ContractSettlementKpiRouteTest` passed, `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.

- Command: `cd backend && .\mvnw.cmd -q test-compile`
- Exit code: `0`
- Result: backend test sources compile.

- Command: `cd backend && .\mvnw.cmd -q '-Dtest=*Contract*Test,*Settlement*Test' test`
- Exit code: `0`
- Result:
  - `ContractApprovalIntegrationTest`: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
  - `ContractApprovalRollbackTest`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`
  - `ContractSettlementKpiRouteTest`: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

## Acceptance Criteria Mapping

- `GET /api/contracts/kpi` no longer falls through to the numeric id route because `GET /contracts/kpi` now exists under the backend `/api` context path.
- `GET /api/settlements/kpi` no longer falls through to the numeric id route because `GET /settlements/kpi` now exists under the backend `/api` context path.
- Numeric id routes remain unchanged: existing `GET /contracts/{id}` and `GET /settlements/{id}` methods were not modified.
- Focused backend tests cover both KPI routes.
- Focused backend verification passed.

## Known Risks

- The KPI aggregation is intentionally simple and scoped to the existing frontend fields; it does not introduce new filter semantics beyond fields already used by the current list/KPI calls.
- Did not run the full backend test suite; ran `test-compile`, the new focused route test, and the task-suggested contract/settlement test selection.
- The working tree contains unrelated pre-existing/uncommitted frontend and coordination-file changes; this task only intentionally changed the files listed above.
