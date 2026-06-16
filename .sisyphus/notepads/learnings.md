
### 2026-06-15 19:20 — P1 nginx security headers fix
- frontend-admin/nginx.conf: HTTP block replaced with 301 redirect, HSTS uncommented with includeSubDomains+preload, CSP header added after HSTS
- Nginx not available on Windows dev machine for syntax check — verify in Docker build

### 2026-06-15 19:33 — Backend regression test gate (Wave 3)
- `./mvnw.cmd test "-Dspring.profiles.active=local"` → BUILD SUCCESS
- Tests: **235** run, **0** failures, **0** errors, **0** skipped (threshold: ≥174)
- 32 test classes all green — StlSettlementService, PayApplicationService, InvoiceService, CostStrategy, AuthController rate-limit all passing
- PowerShell note: must quote `"-Dspring.profiles.active=local"` to prevent pwsh from eating the dot-separated JVM arg

