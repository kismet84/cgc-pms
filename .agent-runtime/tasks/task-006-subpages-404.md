# task-006-subpages-404

## Title

Investigate Docker HTTP subpages showing 404.

## Status

Completed.

## User Report

After enabling local HTTP access, the in-app browser can open the project, but subpages show 404. Current browser URL:

- `http://localhost/order`

## Goal

Identify whether the 404 is produced by Nginx or by Vue Router, then fix the root cause.

## Investigation Plan

- Checked HTTP status and response body for representative subpage URLs.
- Inspected frontend Nginx logs and confirmed subpage URLs return the SPA HTML with HTTP 200.
- Compared frontend menu paths with Vue Router route definitions.
- Added a failing unit test for nested menu item keys.
- Fixed `SidebarMenu.vue` so nested menu items use full route paths.

## Findings

- Nginx was not returning HTTP 404. Representative URLs such as `/order`, `/variation/order`, `/warehouse`, `/inventory/warehouse`, `/application`, and `/payment/application` all returned SPA HTML with HTTP 200.
- The visible 404 was produced by Vue Router.
- Root cause: `SidebarMenu.vue` used each route record's raw `route.path` as the menu key. Nested child records have relative paths such as `order`, `warehouse`, and `application`, so menu clicks navigated to `/order`, `/warehouse`, and `/application`.
- Actual route paths are nested:
  - `/variation/order`
  - `/inventory/warehouse`
  - `/payment/application`
  - `/material/dictionary`
  - `/contract/ledger`

## Changes

- `frontend-admin/src/layouts/components/SidebarMenu.vue`
  - Build full menu keys by joining parent route path with child route path.
- `frontend-admin/src/layouts/components/__tests__/SidebarMenu.test.ts`
  - Added a regression test proving nested menu keys contain full paths and not relative child paths.

## Verification

- `pnpm vitest run src/layouts/components/__tests__/SidebarMenu.test.ts`
  - First run failed as expected before the fix: keys did not include `/variation/order`.
  - Second run passed after the fix.
- `pnpm build`
  - Passed. Vite emitted only the existing large chunk warning.
- `docker compose -f docker-compose.prod.yml up -d --build frontend`
  - Rebuilt and recreated the Docker frontend container. Compose also recreated backend due to the compose build/dependency graph.
- Docker containers are healthy:
  - `cgc-pms-frontend`
  - `cgc-pms-backend`
  - `cgc-pms-mysql`
  - `cgc-pms-redis`
  - `cgc-pms-minio`
- In-app browser verification over `http://localhost`:
  - `/variation/order`: no 404, title `变更签证 - 建筑工程总包项目管理系统`
  - `/inventory/warehouse`: no 404, title `仓库管理 - 建筑工程总包项目管理系统`
  - `/payment/application`: no 404, title `付款申请 - 建筑工程总包项目管理系统`
  - `/material/dictionary`: no 404, title `材料字典 - 建筑工程总包项目管理系统`
  - `/contract/ledger`: no 404, title `合同台账 - 建筑工程总包项目管理系统`

## Residual Observation

- While verifying `/contract/ledger`, the page route itself no longer showed 404, but the frontend container log showed a separate backend error:
  - `GET /api/contracts/kpi HTTP/1.1" 500`
- This is outside the subpage-404 root cause and should be tracked separately if it affects the contract ledger page.

## Acceptance Criteria

- Subpages reachable from the deployed UI no longer show 404.
- Direct browser access to representative full subpage URLs works under `http://localhost`.
- Docker frontend is rebuilt/restarted and verification is recorded.

## Constraints

- Keep the fix scoped to deployed frontend routing/access.
- Preserve HTTPS behavior and `/api/` proxy behavior.
