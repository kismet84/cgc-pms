# Task 24: Phase 3 Integration Test — Learnings

## Environment Setup

### Problem: ApplicationContext failed to load with @ActiveProfiles("local")
- Root cause: `application-local.yml` configures MySQL datasource, but MySQL not running
- Solution: Created `src/test/resources/application-local.yml` that overrides:
  - datasource to H2 (jdbc:h2:mem:cgcpms_test;MODE=MySQL)
  - flyway.enabled=false (MySQL-specific migrations don't work on H2)
  - sql.init.mode=always with H2-compatible schema files

### Problem: H2 schema missing Phase 2/3 tables
- Root cause: H2 schema.sql only has base tables (sys_*, pm_project, md_partner, ct_contract, wf_*, sys_file)
- Solution: Created `db/h2/schema-phase23.sql` with all Phase 2+3 business table DDLs

### Problem: H2 schema missing columns needed by MyBatis-Plus entities
- ct_contract missing: paid_amount, settlement_amount, cost_generated_flag
- cost_subject missing: created_by, updated_by, remark
- pay_record missing: project_id, remark
- Solution: Added these columns to the H2 DDL files

### Problem: H2 demo data missing V6 test IDs (10001, 20001, 30001)
- Root cause: H2 data.sql only has test data with IDs 100/100/100
- Solution: Added V6 demo data to data.sql (projects PRJ-2026-001/002, partners, contracts CT-2026-001/002/003)

### Problem: Approval templates for Phase 3 business types not available
- Root cause: Flyway V9/V13-V17/V28-V30 use MySQL-specific JSON_OBJECT()
- Solution: Added MERGE statements for Phase 3 templates (CT_CHANGE, SETTLEMENT, COST_TARGET, VAR_ORDER, SUB_MEASURE)

## Test Results

All 6 tests pass with H2 local profile:
1. ✅ CT_CHANGE full chain — change creation → approval → currentAmount update + cost generation
2. ✅ Settlement full chain — sub_measure → settlement → approval → no cost from settlement
3. ✅ Dynamic cost formula — refreshSummary → verify formula correctness
4. ✅ Target cost lifecycle — create → approve → version activation → cost_summary linkage
5. ✅ Alert trigger — set expiring contract → CONTRACT_EXPIRING alert generated
6. ✅ CT_CHANGE + VAR_ORDER coexistence — distinct cost items, no double-counting

## Key Files Modified

- `backend/src/test/resources/application-local.yml` — NEW: H2 test config
- `backend/src/main/resources/db/h2/schema-phase23.sql` — NEW: Phase 2+3 table DDL
- `backend/src/main/resources/db/h2/schema.sql` — MODIFIED: added ct_contract columns
- `backend/src/main/resources/db/h2/data.sql` — MODIFIED: added V6 demo data
- `backend/src/test/java/com/cgcpms/Phase3IntegrationTest.java` — MODIFIED: fixed test05 alert trigger
