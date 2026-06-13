# task-005-allow-http-local-access

## Title

Allow local HTTP access for Docker frontend.

## Status

Completed.

## User Request

The Docker deployment works in the system browser over HTTPS, but the in-app browser is blocked on local HTTPS. The user approved allowing local HTTP access.

## Goal

Make `http://localhost/dashboard` work for the Docker frontend while keeping existing HTTPS access available.

## Plan

- Update the frontend Nginx `listen 80` server block so it serves the SPA and proxies `/api/` instead of returning `301` to HTTPS.
- Keep the existing HTTPS server block unchanged.
- Rebuild and recreate only the frontend Docker container.
- Verify:
  - `http://localhost/dashboard` returns the SPA.
  - `http://localhost/api/notifications/unread-count` reaches the backend and returns an auth-aware API response.
  - The in-app browser can open `http://localhost/dashboard`.

## Result

- Updated `frontend-admin/nginx.conf` so port `80` serves the SPA directly and proxies `/api/` to the backend.
- Kept the existing HTTPS server block unchanged.
- Rebuilt and recreated the Docker frontend container with:
  - `docker compose -f docker-compose.prod.yml up -d --build frontend`
- Docker Compose also recreated the backend container because the compose file includes backend build/dependency metadata.
- Verified containers are healthy:
  - `cgc-pms-frontend`
  - `cgc-pms-backend`
  - `cgc-pms-mysql`
  - `cgc-pms-redis`
  - `cgc-pms-minio`
- Verified `http://localhost/dashboard` returns `200` with the SPA HTML.
- Verified `http://localhost/api/notifications/unread-count` reaches the backend and returns `401 AUTH_TOKEN_INVALID` without redirect, which is expected without login cookies.
- Verified the in-app browser can open `http://localhost/dashboard`; the application redirects to `http://localhost/login?redirect=/dashboard`, which is the expected unauthenticated route.

## Constraints

- Keep the change limited to Docker frontend HTTP access.
- Do not weaken the HTTPS server block.
- Preserve `/api/` SSE proxy settings for notification streams.
