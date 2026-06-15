# Dimension 1: Security & Configuration Review

**Project**: CGC-PMS (Construction General Contracting Project Management System)
**Date**: 2026-06-15
**Reviewer**: Automated security review agent
**Scope**: @PreAuthorize coverage · JWT implementation · CORS · File upload · Logging · Exception handling · Docker/Nginx security · Env secrets

---

## Summary

| Category | Findings | P0 | P1 | P2 | P3 |
|----------|----------|----|----|----|----|
| Authentication & Authorization | 3 | 0 | 1 | 1 | 1 |
| CORS & Network Security | 1 | 0 | 0 | 1 | 0 |
| File Upload | 2 | 0 | 0 | 1 | 1 |
| Logging & Exception Handling | 2 | 0 | 0 | 2 | 0 |
| Docker Security | 3 | 0 | 1 | 2 | 0 |
| Nginx & HTTPS | 3 | 0 | 1 | 1 | 1 |
| Configuration & Secrets | 1 | 0 | 1 | 0 | 0 |
| **Total** | **15** | **0** | **4** | **8** | **3** |

---

## Findings (Top 15)

---

### [D1-001] | P1 | deploy/.env.example — Self-referencing placeholder variables

**File**: `deploy/.env.example`
**Lines**: 3, 6, 8, 12, 17

**Issue**: All secret values use self-referencing ${VAR_NAME} placeholders:
- MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
- MYSQL_PASSWORD=${MYSQL_PASSWORD}
- REDIS_PASSWORD=${REDIS_PASSWORD}
- JWT_SECRET=${JWT_SECRET}
- MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD}

Docker Compose does not recursively expand self-referencing variables in .env files. When a developer runs `cp .env.example .env` followed by `docker compose up`, the literal string `${MYSQL_ROOT_PASSWORD}` (not a real password) is passed to containers, resulting in empty/invalid credentials. An inexperienced user will silently get an insecure deployment.

**Reproduction**:
1. `cp deploy/.env.example deploy/.env`
2. `docker compose -f deploy/docker-compose.prod.yml up -d`
3. Secrets resolve to literal `${VAR}` strings or empty values.

**Fix**: Replace self-references with obvious placeholder strings:
MYSQL_ROOT_PASSWORD=CHANGE-ME-ROOT-PASSWORD
MYSQL_PASSWORD=CHANGE-ME-CGC-PASSWORD
REDIS_PASSWORD=CHANGE-ME-REDIS-PASSWORD
JWT_SECRET=CHANGE-ME-JWT-SECRET-MUST-BE-32-CHARS-MIN
MINIO_ROOT_PASSWORD=CHANGE-ME-MINIO-PASSWORD

---

### [D1-002] | P1 | deploy/docker-compose.prod.yml:133 & backend/Dockerfile:54 — Production JDBC URL overrides secure SSL default

**File**: `deploy/docker-compose.prod.yml`, line 133
**File**: `backend/Dockerfile`, line 54

**Issue**: `application-prod.yml` defaults to `useSSL=true`, but both `backend/Dockerfile` and `deploy/docker-compose.prod.yml` explicitly override with `useSSL=false&allowPublicKeyRetrieval=true`. For production on shared or multi-tenant Docker hosts, database traffic between backend and MySQL containers is unencrypted, violating defense-in-depth.

**Reproduction**: In docker-compose.prod.yml line 133:
SPRING_DATASOURCE_URL: "jdbc:mysql://mysql:3306/cgc_pms?...useSSL=false..."

**Fix**: Replace with `useSSL=true` or make configurable via .env:
SPRING_DATASOURCE_URL: "jdbc:mysql://mysql:3306/cgc_pms?...useSSL=${DB_USE_SSL:-true}"

---

### [D1-003] | P1 | frontend-admin/nginx.conf:107 — HSTS header commented out; no HTTP-to-HTTPS redirect

**File**: `frontend-admin/nginx.conf`, line 107

**Issue**: Strict-Transport-Security header is commented out (line 107). The HTTP server block (lines 36-80) serves content without redirecting to HTTPS. A deployer who copies this config verbatim gets no HSTS protection and plain HTTP access remains available.

**Reproduction**:
1. Deploy with real CA-signed certs
2. Visit http://domain.com -> serves over HTTP, no HSTS on HTTPS responses

**Fix**:
1. Uncomment `add_header Strict-Transport-Security "max-age=31536000" always;`
2. Replace HTTP server block with permanent redirect:
   return 301 https://$host$request_uri;

---

### [D1-004] | P1 | backend/.../auth/controller/AuthController.java:42,79 — Public login/refresh endpoints lack rate limiting

**File**: `backend/src/main/java/com/cgcpms/auth/controller/AuthController.java`
**Lines**: 42 (/auth/login), 79 (/auth/refresh)

**Issue**: Both endpoints are correctly public but have no rate limiting, account lockout, CAPTCHA, or brute-force protection. An attacker can repeatedly POST to /auth/login with common passwords without being throttled.

**Reproduction**: Sequential curl requests to POST /auth/login with different passwords -> no throttling occurs.

**Fix**: Add one or more of:
- Rate limiter on /auth/login (e.g., 5 attempts/min per IP per username)
- Account lockout after N consecutive failures (store in Redis)
- CAPTCHA after N failed attempts

---

### [D1-005] | P2 | backend/.../auth/util/CookieUtils.java:23 — Secure flag defaults to false

**File**: `backend/src/main/java/com/cgcpms/auth/util/CookieUtils.java`, line 23

**Issue**: `@Value("${cookie.secure:false}")` defaults Secure flag to false. Only prod profile sets `cookie.secure: true`. Dev/local profiles omit it. If the app runs behind HTTPS in staging or review environments, auth cookies lack the Secure flag.

**Reproduction**: Run with SPRING_PROFILES_ACTIVE=dev behind HTTPS. Set-Cookie headers lack `; Secure`.

**Fix**: Set `cookie.secure: true` in application-dev.yml, or change default to true and override only in local profile.

---

### [D1-006] | P2 | backend/.../auth/config/SecurityConfig.java:45-46 — Missing security headers in Spring Security

**File**: `backend/src/main/java/com/cgcpms/auth/config/SecurityConfig.java`, lines 45-46

**Issue**: The headers config only disables XSS protection but sets no positive security headers. While Nginx sets headers for HTTPS, they are absent when accessing the backend directly or via HTTP.

**Reproduction**: `curl -I http://localhost:8080/api/actuator/health` -> no X-Frame-Options, CSP, Permissions-Policy headers.

**Fix**: Configure Spring Security headers:
.headers(headers -> headers
    .frameOptions(frame -> frame.deny())
    .contentTypeOptions(Customizer.withDefaults())
    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
)

---

### [D1-007] | P2 | backend/.../resources/logback-spring.xml — Log masking regex misses JSON/structured formats

**File**: `backend/src/main/resources/logback-spring.xml`, lines 7, 25, 40

**Issue**: The regex only matches `key=value` / `key: value` delimited by whitespace/commas/semicolons. It misses:
- JSON: `{"password": "secret", "token": "abc"}`
- XML: `<password>secret</password>`
- Multi-line patterns
- URL query parameters: `?token=abc&password=secret`

**Reproduction**: Log `{"token":"eyJhbGci...","phone":"13800138000"}` -> token and phone are NOT masked.

**Fix**: Either use structured logging with field-level filtering (Logstash encoder) or add a second regex for JSON patterns:
`%replace(%msg){'"[^"]*"\s*:\s*"[^"]*"', '***MASKED***'}`

---

### [D1-008] | P2 | backend/.../common/exception/GlobalExceptionHandler.java — Exception messages may leak sensitive data

**File**: `backend/src/main/java/com/cgcpms/common/exception/GlobalExceptionHandler.java`, lines 73-86

**Issue**: The HttpMessageNotReadableException handler (line 85) includes the most specific cause message in the API response. If a JSON parse error occurs while processing a request with sensitive payload (password, token), the error response may include part of that data.

**Reproduction**: Send malformed JSON with `{"password": "s3cret"}` -> error response may leak part of payload.

**Fix**: Remove the `detail` from API responses (it is already logged server-side). Limit detail inclusion to non-prod profiles.

---

### [D1-009] | P2 | backend/Dockerfile:54-75 — Empty default values for critical secrets

**File**: `backend/Dockerfile`, lines 54-75

**Issue**: The Dockerfile sets `ENV` defaults to empty strings for JWT_SECRET, DB_PASSWORD, MINIO_SECRET_KEY, and SPRING_DATA_REDIS_PASSWORD. Running the image standalone (`docker run cgc-pms-backend`) starts with blank secrets. JWT signing with an empty key is a critical vulnerability.

**Reproduction**: `docker run -e SPRING_PROFILES_ACTIVE=prod cgc-pms-backend:latest` -> JWT_SECRET is empty.

**Fix**: Remove ENV defaults for secrets, or use obvious placeholders with startup validation:
ENV JWT_SECRET=__MUST_OVERRIDE_IN_PRODUCTION__
Add @PostConstruct validation to fail fast if secrets are still placeholder values.

---

### [D1-010] | P2 | backend/Dockerfile & docker-compose files — useSSL=false in all Docker deployments

**File**: `backend/Dockerfile:54`, `deploy/docker-compose.prod.yml:133`, `deploy/docker-compose.dev.yml:117`

**Issue**: All Docker configurations hardcode useSSL=false in the JDBC URL. For multi-tenant or regulated environments, database traffic over Docker bridge/overlay networks should be encrypted. The application-prod.yml default (useSSL=true) is consistently overridden.

**Reproduction**: docker-compose.prod.yml line 133 overrides the secure default with useSSL=false.

**Fix**: Make useSSL a .env variable: `SPRING_DATASOURCE_URL: "...useSSL=${DB_USE_SSL:-true}"` and remove hardcoded URL from Dockerfile.

---

### [D1-011] | P2 | frontend-admin/nginx.conf — Missing Content-Security-Policy; HTTP block lacks all security headers

**File**: `frontend-admin/nginx.conf`, lines 36-80 (HTTP), lines 85-164 (HTTPS)

**Issue**:
1. HTTP server block has NO security headers (no X-Frame-Options, no CSP, no Referrer-Policy)
2. HTTPS server block has no Content-Security-Policy header (most effective XSS defense)

**Reproduction**: `curl -I https://domain.com/` -> no Content-Security-Policy header.

**Fix**: Add CSP to HTTPS block and duplicate headers to HTTP block (or redirect HTTP to HTTPS):
add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'" always;

---

### [D1-012] | P2 | backend/Dockerfile & docker-compose.prod.yml — Missing container security constraints

**File**: `backend/Dockerfile:83`, `deploy/docker-compose.prod.yml:114-161`

**Issue**: Backend runs as non-root user (good) but lacks:
- read_only root filesystem
- cap_drop: ALL
- security_opt: no-new-privileges
- seccomp/AppArmor profile
A compromised process has default container capabilities and writable filesystem.

**Reproduction**: `docker inspect cgc-pms-backend` -> full capability set, writable FS.

**Fix**: In docker-compose.prod.yml backend service:
security_opt:
  - no-new-privileges:true
cap_drop:
  - ALL
read_only: true
tmpfs:
  - /tmp:noexec,nosuid,size=100M

---

### [D1-013] | P2 | backend/.../file/service/FileService.java:42-46 — Extension whitelist incomplete; no MIME validation

**File**: `backend/src/main/java/com/cgcpms/file/service/FileService.java`, lines 42-46

**Issue**: The extension whitelist (20 types) is well-defined but:
1. Missing macro-enabled Office types (.docm, .xlsm, .pptm) which could be used for malware delivery
2. No MIME type validation - a .exe renamed to .pdf is accepted since only the extension is checked

**Reproduction**: Rename malware.exe to invoice.pdf, upload via POST /files/upload -> accepted.

**Fix**: Add MIME type validation against claimed extension. Add optional macro types if business requires them.

---

### [D1-014] | P3 | backend/.../auth/config/SecurityConfig.java:46 — XSS protection disabled without CSP alternative

**File**: `backend/src/main/java/com/cgcpms/auth/config/SecurityConfig.java`, line 46

**Issue**: `.xssProtection(xss -> xss.disable())` disables the deprecated X-XSS-Protection header without enabling Content-Security-Policy as a modern replacement. Nginx sets the same deprecated header but no CSP at any layer.

**Reproduction**: Security audit shows XSS header disabled. curl -I shows no CSP.

**Fix**: Replace with Content-Security-Policy header:
.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))

---

### [D1-015] | P3 | deploy/.env.example:16 — MINIO_ROOT_USER hardcoded to default "admin"

**File**: `deploy/.env.example`, line 16

**Issue**: MINIO_ROOT_USER=admin is MinIO's well-known default. An attacker with access to .env or who discovers the MinIO endpoint knows 50% of the admin credentials by default.

**Reproduction**: .env.example produces MINIO_ROOT_USER=admin. Deployer who doesn't change it uses the default.

**Fix**: Change to a unique suggestion: MINIO_ROOT_USER=CHANGE-ME-MINIO-USER

---

## Appendix: Additional Minor Findings (overflow from 15 limit)

| ID | Priority | File:Line | Issue |
|----|----------|-----------|-------|
| A-01 | P3 | backend/Dockerfile:55 | SPRING_DATASOURCE_URL hardcodes serverTimezone=Asia/Shanghai - should be configurable |
| A-02 | P3 | frontend-admin/nginx.conf:17-31 | gzip_types missing font/woff, text/html not explicitly listed |
| A-03 | P3 | application-prod.yml:68 | actuator info endpoint could leak build metadata in production |
| A-04 | P3 | deploy/.env.example | MINIO_ROOT_PASSWORD self-reference problem (same as D1-001) |
| A-05 | P3 | backend/Dockerfile:42 | MaxRAMPercentage=75.0 may conflict with hard -Xmx1g on container-limited hosts |
| A-06 | P3 | logback-spring.xml:52 | Prod com.cgcpms set to INFO - consider dynamic log-level via actuator |

---

## Positive Findings (Strengths Observed)

- **Complete @PreAuthorize coverage**: All 41 controllers have matching @PreAuthorize counts for every endpoint. AuthController's /login and /refresh are intentionally public.
- **No empty catch blocks**: Zero instances found across the entire codebase.
- **AuthorizationDeniedException -> 403**: GlobalExceptionHandler correctly maps to FORBIDDEN, not INTERNAL_SERVER_ERROR.
- **JWT dual-token scheme**: Access 15min + refresh 7d with Redis blacklist and token rotation on refresh.
- **File upload path injection prevention**: businessType validated with `[A-Za-z0-9_-]+` regex before use in storage path.
- **Tenant isolation in file access**: getPresignedUrl and delete verify tenantId ownership.
- **All containers have HEALTHCHECK**: backend, frontend, MySQL, Redis, MinIO all configured.
- **Non-root user in Docker**: backend runs as appuser, frontend runs as nginx default non-root.
- **SSL/TLS configured**: TLSv1.2/TLSv1.3 only, modern ciphers, http2 enabled.
- **CORS properly restricted**: allowed-origins driven by configuration per profile, no wildcard origins.

## CORS Verification

CorsConfig (`backend/src/main/java/com/cgcpms/auth/config/CorsConfig.java`):
- Uses `@Value("${cors.allowed-origins}")` - driven by configuration, no hardcoded wildcards
- `allowedMethods` restricted to GET/POST/PUT/PATCH/DELETE/OPTIONS
- `allowedHeaders` restricted to Authorization/Content-Type/X-Refresh-Token
- `allowCredentials(true)` - safe when combined with specific origins
- Dev profile: `cors.allowed-origins: "http://localhost:5173"`
- Prod profile: `cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:https://your-production-domain.com}`
- **No wildcard origins** in any profile - well configured.
