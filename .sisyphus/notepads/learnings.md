
### 2026-06-15 19:20 — P1 nginx security headers fix
- frontend-admin/nginx.conf: HTTP block replaced with 301 redirect, HSTS uncommented with includeSubDomains+preload, CSP header added after HSTS
- Nginx not available on Windows dev machine for syntax check — verify in Docker build

