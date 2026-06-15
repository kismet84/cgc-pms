# Dimension 4 — Data & Infrastructure Review

**Date:** 2026-06-15
**Scope:** Flyway migration sync, database schema quality, Docker best practices, Docker Compose config, CI/CD pipeline
**Method:** Static analysis of migration files, Dockerfiles, compose files, and CI workflow

---

## Summary

| Metric | Value |
|--------|-------|
| MySQL migration files | 51 (`db/migration/V*.sql`) |
| H2 migration files | 51 (`db/migration-h2/V*.sql`) |
| File name match rate | **100%** (all 51 pairs identical) |
| Severity breakdown | P1: 2 | P2: 6 | P3: 7 |
| **Total findings** | **15** |

---

## Findings

### [D4-001] | P2 | backend/src/main/resources/db/migration/V47__add_user_preference.sql:27
- **Issue**: `deleted_flag` column uses `SMALLINT NOT NULL DEFAULT 0` in V47's `sys_user_preference` table, while all V1-V45 tables consistently use `TINYINT NOT NULL DEFAULT 0`. This type inconsistency could cause ORM mapping issues in Java entity classes.
- **Evidence**: V47 line 27: `deleted_flag SMALLINT NOT NULL DEFAULT 0` vs V1 line 30: `deleted_flag TINYINT NOT NULL DEFAULT 0` (and all other migration files).
- **Fix**: Change `SMALLINT` to `TINYINT` in V47 (and V47 H2) to match project convention.

### [D4-002] | P2 | backend/src/main/resources/db/migration/V47__add_user_preference.sql:26 / backend/src/main/resources/db/migration-h2/V47__add_user_preference.sql:18
- **Issue**: MySQL V47 uses `CONSTRAINT uk_tenant_user UNIQUE (tenant_id, user_id)` (named constraint) while H2 V47 uses `UNIQUE (tenant_id, user_id)` (unnamed). H2 will auto-generate a system name, making future cross-DB constraint drops impossible by name.
- **Evidence**: MySQL line 26: `CONSTRAINT uk_tenant_user UNIQUE ...` vs H2 line 18: `UNIQUE (tenant_id, user_id)`.
- **Fix**: Add constraint name in H2 version: `CONSTRAINT uk_tenant_user UNIQUE (tenant_id, user_id)`.

### [D4-003] | P3 | backend/src/main/resources/db/migration/V50__fix_invoice_unique_with_deleted_flag.sql:5 / V51__fix_project_unique_with_deleted_flag.sql:5
- **Issue**: Inconsistent syntax for dropping unique constraints. V50 uses `DROP INDEX uk_pi_tenant_invoice_no` while V51 uses `DROP UNIQUE (tenant_id, project_code)`.
- **Evidence**: V50: `ALTER TABLE pay_invoice DROP INDEX uk_pi_tenant_invoice_no;` | V51: `ALTER TABLE pm_project DROP UNIQUE (tenant_id, project_code);`
- **Fix**: Unify both to use `DROP INDEX <constraint_name>` (MySQL-compatible syntax).

### [D4-004] | P2 | backend/src/main/resources/db/migration/V22__init_cost_target_tables.sql through V44__add_workflow_action_permissions.sql
- **Issue**: `created_at`/`updated_at` naming (V1-V21) was changed to `created_time`/`updated_time` in V22-V44 before V45 unified them back. This 23-migration naming split means application code compiled against V22-V44 schema that references `created_time` will break after V45 applies.
- **Evidence**: V22 line 30: `created_time DATETIME` | V45 lines 7-79 renames 16+ tables | V1 line 24: `created_at DATETIME`
- **Fix**: Ensure all Java entities use `createdAt`/`updatedAt` field names via `MyMetaObjectHandler`. No code should reference the old column names.

### [D4-005] | P3 | backend/src/main/resources/db/migration/V8__add_missing_indexes.sql
- **Issue**: V8 only adds `created_at` sort indexes for 4 tables (`sys_user`, `pm_project`, `md_partner`, `ct_contract`). Many other tables (e.g., `cost_item`, `pay_application`, `pay_record`, `wf_instance`, `sys_notification`, `pay_invoice`) queried by `created_at DESC` lack sort indexes, triggering `filesort` at scale.
- **Evidence**: V8 has 4 `CREATE INDEX ... created_at` statements. No other migration files create `created_at` indexes beyond these 4.
- **Fix**: Audit all tables with list-view queries sorted by `created_at` and add indexes.

### [D4-006] | P2 | All migration files in backend/src/main/resources/db/migration/V*.sql
- **Issue**: No database-level `FOREIGN KEY` constraints exist across all 51 migrations. All referential integrity is application/ORM-level only. This risks orphaned records on application bugs or direct SQL operations.
- **Evidence**: Search for "FOREIGN KEY" in all migration files returns zero matches.
- **Fix**: Document this as a known risk in database design docs. Not recommended to add FK constraints (MyBatis-Plus + Snowflake IDs).

### [D4-007] | P1 | backend/Dockerfile:56,63,70,75
- **Issue**: Four critical environment variables have empty default values: `DB_PASSWORD=`, `SPRING_DATA_REDIS_PASSWORD=`, `MINIO_SECRET_KEY=`, `JWT_SECRET=`. Running the image directly (without docker-compose) starts with empty credentials.
- **Evidence**: Lines 56, 63, 70, 75: `ENV DB_PASSWORD=`, `ENV SPRING_DATA_REDIS_PASSWORD=`, `ENV MINIO_SECRET_KEY=`, `ENV JWT_SECRET=`
- **Fix**: Remove empty ENV lines or add startup validation that fails if these are empty.

### [D4-008] | P2 | frontend-admin/Dockerfile:18
- **Issue**: `# COPY public/ ./public/` is commented out. Static assets placed in `public/` directory (favicon, robots.txt, PWA assets) will be missing from production image.
- **Evidence**: Line 18: `# COPY public/ ./public/`
- **Fix**: Uncomment: `COPY public/ ./public/` (or verify vite embeds them in build output).

### [D4-009] | P3 | .github/workflows/ci.yml:79 vs frontend-admin/Dockerfile:5
- **Issue**: CI installs `pnpm@11` (major only) while Dockerfile pins `pnpm@11.0.9`. Version mismatch between CI and Docker builds.
- **Evidence**: CI line 79: `npm install -g pnpm@11` | Dockerfile line 5: `corepack prepare pnpm@11.0.9 --activate`
- **Fix**: Pin to same version: `npm install -g pnpm@11.0.9`

### [D4-010] | P3 | deploy/docker-compose.prod.yml:15
- **Issue**: Uses deprecated `version: "3.8"` field (Docker Compose v2 ignores it).
- **Fix**: Remove the `version` field.

### [D4-011] | P3 | deploy/docker-compose.yml
- **Issue**: Dev compose has no resource limits while prod compose has limits on all 5 services. Dev environments can exhaust host memory.
- **Fix**: Add `deploy.resources.limits.memory` to dev compose services.

### [D4-012] | P2 | .github/workflows/ci.yml:258-259
- **Issue**: CI deploy job has pre-deploy DB backup commented out. No automated rollback if deployment fails.
- **Evidence**: Lines 258-259: `# Pre-deploy DB backup (uncomment when backup script exists)`
- **Fix**: Implement backup script and uncomment. Add automated rollback on health check failure.

### [D4-013] | P3 | .github/workflows/ci.yml:152
- **Issue**: `flyway-check` job uses `timeout 60` for `./mvnw spring-boot:run`. Cold cache Maven resolution can take 30-60s, risking flaky CI failures.
- **Fix**: Increase timeout to 120s or use `mvn flyway:migrate` directly.

### [D4-014] | P2 | .github/workflows/ci.yml:183,214
- **Issue**: `docker-build` job skips on `workflow_dispatch` (`if: github.event_name != 'workflow_dispatch'`), so deploy builds its own images without GHA cache.
- **Fix**: Remove the skip condition from `docker-build` or have `deploy` depend on it.

### [D4-015] | P1 | deploy/.env
- **Issue**: Production credentials in plaintext (`MYSQL_ROOT_PASSWORD`, `JWT_SECRET`, etc.). While `.gitignore` correctly excludes it, any filesystem compromise exposes all secrets.
- **Evidence**: Cat `deploy/.env` shows plaintext passwords. `git check-ignore deploy/.env` confirms git exclusion.
- **Fix**: Use Docker secrets or a vault solution. At minimum: `chmod 600 deploy/.env`.

---

## Coverage Checklist

| Area | Status | Notes |
|------|--------|-------|
| Flyway sync (file count) | **PASS** | 51 MySQL = 51 H2, names match 100% |
| MySQL-only syntax in H2 | **PASS** | H2 files have zero ENGINE/COLLATE/SET NAMES/FOREIGN_KEY_CHECKS |
| Index on FK columns | **PARTIAL** | Most FK columns indexed; V8 scope is limited (D4-005) |
| created_at vs created_time | **HISTORICAL** | V22-V44 used `_time`, fixed in V45 (D4-004) |
| Backend Dockerfile | **2 FINDINGS** | Empty credential defaults (P1), good multi-stage/HEALTHCHECK/non-root |
| Frontend Dockerfile | **2 FINDINGS** | COPY public/ commented out (P2), good .dockerignore/HEALTHCHECK |
| docker-compose.prod.yml | **1 FINDING** | Deprecated version field (P3) |
| CI/CD pipeline | **3 FINDINGS** | Pre-deploy backup missing (P2), timeout risk (P3), docker-build skip (P2) |
| Security | **2 FINDINGS** | Empty Dockerfile defaults (P1), plaintext .env credentials (P1) |
