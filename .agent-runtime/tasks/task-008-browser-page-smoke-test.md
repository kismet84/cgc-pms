# task-008-browser-page-smoke-test

## Title

Smoke test all frontend pages with the in-app browser.

## Status

Completed.

## User Request

Use the in-app browser to simulate testing every page and check whether there are errors.

## Scope

Test the Docker-deployed frontend over local HTTP:

- Base URL: `http://localhost`
- Browser: Codex in-app browser
- Pages: frontend routes defined in `frontend-admin/src/router/index.ts`

## Checks

- Page can be opened.
- Page does not show the Vue 404 page.
- Page does not show visible Ant Design error messages.
- Browser console does not show new errors for the tested page.
- Docker frontend/backend logs do not show unexpected 4xx/5xx for the tested page.

## Notes

- Dynamic detail pages that require real IDs should be skipped or tested only through known existing IDs if available.
- Authentication is expected to be available from the current browser session. If not, login first.

## Acceptance Criteria

- A page-by-page result table is recorded.
- Any failing page has URL, symptom, and relevant log evidence.
- No business code is changed during testing unless the user asks for fixes.

## Result Summary

- Tested at: 2026-06-13 15:32 Asia/Shanghai.
- Tested pages: 26.
- Passed pages: 24.
- Failed pages: 2.
- Vue 404 pages: 0.
- New browser dev console errors captured by the in-app browser during page navigation: 0.
- Docker containers were running and healthy during the test.
- No business code was changed by this smoke test.

## Page Results

| Page | URL | Result | Notes |
| --- | --- | --- | --- |
| Login | `http://localhost/login` | Pass | Login page opens. |
| Dashboard | `http://localhost/dashboard` | Pass | Dashboard opens after auth state settles. |
| Contract Ledger | `http://localhost/contract/ledger` | Fail | Shows `系统异常，请稍后重试` and `加载合同指标失败，请稍后重试`. |
| Contract Create | `http://localhost/contract/create` | Pass | Page opens. |
| Cost Ledger | `http://localhost/cost/ledger` | Pass | Page opens. |
| Cost Summary | `http://localhost/cost/summary` | Pass | Page opens. |
| Cost Target Index | `http://localhost/cost-target/index` | Pass | Page opens. |
| Cost Target Create | `http://localhost/cost-target/create` | Pass | Page opens. |
| Variation Order | `http://localhost/variation/order` | Pass | Page opens; no submenu 404. |
| Settlement List | `http://localhost/settlement/list` | Fail | Shows `系统异常，请稍后重试`. |
| Project List | `http://localhost/project/list` | Pass | Page opens. |
| Organization | `http://localhost/org` | Pass | Page opens. |
| Subcontract Task | `http://localhost/subcontract/task` | Pass | Page opens. |
| Subcontract Measure | `http://localhost/subcontract/measure` | Pass | Page opens. |
| Purchase Order | `http://localhost/purchase/order` | Pass | Page opens. |
| Purchase Receipt | `http://localhost/purchase/receipt` | Pass | Page opens. |
| Payment Application | `http://localhost/payment/application` | Pass | Page opens; no submenu 404. |
| Inventory Warehouse | `http://localhost/inventory/warehouse` | Pass | Page opens; no submenu 404. |
| Inventory Stock | `http://localhost/inventory/stock` | Pass | Page opens. |
| Inventory Transaction | `http://localhost/inventory/transaction` | Pass | Page opens. |
| Inventory Purchase Request | `http://localhost/inventory/purchase-request` | Pass | Page opens. |
| Invoice | `http://localhost/invoice` | Pass | Page opens. |
| Material Dictionary | `http://localhost/material/dictionary` | Pass | Page opens. |
| Alert | `http://localhost/alert` | Pass | Page opens. |
| Approval Todo | `http://localhost/approval/todo` | Pass | Page opens. |
| System Dict | `http://localhost/system/dict` | Pass | Page opens. |

## Error Evidence

### Contract KPI

- Frontend request: `GET /api/contracts/kpi`
- Browser page: `http://localhost/contract/ledger`
- Frontend proxy result: HTTP 500.
- Visible message: `系统异常，请稍后重试`, `加载合同指标失败，请稍后重试`.
- Backend root error:
  - `MethodArgumentTypeMismatchException`
  - Method parameter `id` failed to convert `kpi` to `Long`.
  - Cause: `NumberFormatException: For input string: "kpi"`.

Likely cause: the backend route for `/api/contracts/kpi` is being captured by a generic `/{id}` mapping before the static `kpi` mapping.

### Settlement KPI

- Frontend request: `GET /api/settlements/kpi`
- Browser page: `http://localhost/settlement/list`
- Frontend proxy result: HTTP 500.
- Visible message: `系统异常，请稍后重试`.
- Backend root error:
  - `MethodArgumentTypeMismatchException`
  - Method parameter `id` failed to convert `kpi` to `Long`.
  - Cause: `NumberFormatException: For input string: "kpi"`.

Likely cause: the backend route for `/api/settlements/kpi` is being captured by a generic `/{id}` mapping before the static `kpi` mapping.

## Additional Observations

- During the first authenticated page load, the frontend log showed transient 401 responses for notification-related requests, followed by successful requests. No visible page error was observed from those transient responses during this smoke test.
- Backend logs still contain notification SSE async-dispatch security/committed-response noise after an initial stream request, but this did not surface as a visible page failure during the tested navigation set.

## Recommended Next Task

Open a fix task for the two KPI route conflicts:

- Ensure `/api/contracts/kpi` is resolved by the KPI controller method, not the generic contract id method.
- Ensure `/api/settlements/kpi` is resolved by the KPI controller method, not the generic settlement id method.
- Add focused backend tests for both KPI endpoints so the static route remains protected from `/{id}` mapping regressions.
