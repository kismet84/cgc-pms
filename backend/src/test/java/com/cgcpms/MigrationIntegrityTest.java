package com.cgcpms;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationIntegrityTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Path H2_MIGRATION_DIR = Path.of("src/main/resources/db/migration-h2");
    private static final Pattern DIRECT_ADD_COST_TARGET_ID = Pattern.compile(
            "(?im)^\\s*ALTER\\s+TABLE\\s+cost_summary\\s*\\R\\s*ADD\\s+COLUMN\\s+cost_target_id");

    @Test
    void partnerDefaultLeadDaysMigrationIsMirroredAcrossDialects() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V155__add_partner_default_lead_days.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V155__add_partner_default_lead_days.sql");
        String expected = "alter table md_partner add column default_lead_days int null;";

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));
        assertEquals(expected, normalizeSql(Files.readString(mysqlMigration)).trim());
        assertEquals(expected, normalizeSql(Files.readString(h2Migration)).trim());
    }

    @Test
    void stockTransferPostingMigrationIsMirroredAcrossDialects() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V211__add_stock_transfer_posting.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V211__add_stock_transfer_posting.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            String normalized = normalizeSql(sql);
            assertTrue(normalized.contains("create table mat_stock_transfer"));
            assertTrue(normalized.contains("uk_stock_transfer_tenant_key"));
            assertTrue(normalized.contains("(tenant_id,idempotency_key)"));
            assertTrue(normalized.contains("foreign key (source_stock_id) references mat_stock (id) on delete restrict"));
            assertTrue(normalized.contains("foreign key (target_stock_id) references mat_stock (id) on delete restrict"));
            assertTrue(normalized.contains("check (source_stock_id <> target_stock_id and source_warehouse_id <> target_warehouse_id)"));
            assertTrue(normalized.contains("check (quantity > 0 and unit_cost >= 0 and amount >= 0)"));
            assertTrue(normalized.contains("check (status in ('pending','completed'))"));
            assertTrue(normalized.contains("idx_stock_transfer_project_created"));
            assertTrue(normalized.contains("idx_stock_transfer_source_stock"));
            assertTrue(normalized.contains("idx_stock_transfer_target_stock"));
        }
    }

    @Test
    void documentGenerationCoreMigrationIsMirroredAcrossDialects() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V212__create_document_generation_core.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V212__create_document_generation_core.sql");
        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));
        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            String normalized = normalizeSql(sql);
            assertTrue(normalized.contains("create table biz_document_template"));
            assertTrue(normalized.contains("create table biz_document_template_version"));
            assertTrue(normalized.contains("create table biz_document_default_binding"));
            assertTrue(normalized.contains("create table biz_document_generation"));
            assertTrue(normalized.contains("active_unique_token"));
            assertFalse(normalized.contains("deleted_token"));
            assertTrue(normalized.contains("document:audit:download"));
            assertTrue(normalized.contains("role_code = 'super_admin'")
                    || normalized.contains("role_code in ('super_admin'"));
        }
    }

    @Test
    void costSubjectV2ClosedLoopMigrationIsMirroredAcrossDialects() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V213__cost_subject_v2_closed_loop.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V213__cost_subject_v2_closed_loop.sql");
        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));
        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            String normalized = normalizeSql(sql);
            assertTrue(normalized.contains("create table cost_subject_mapping_version"));
            assertTrue(normalized.contains("create table cost_subject_assignment_rule"));
            assertTrue(normalized.contains("create table project_cost_subject_scope"));
            assertTrue(normalized.contains("create table bid_cost_target_transfer"));
            assertTrue(normalized.contains("create table finance_cost_allocation_batch"));
            assertTrue(normalized.contains("cost:subject:finance-allocate"));
            assertTrue(normalized.contains("approval_instance_id"));
            assertTrue(normalized.contains("idempotency_key"));
            assertTrue(normalized.contains("reversal_of_id"));
            assertTrue(normalized.contains("bid_cost_target_transfer_reversal"));
            assertTrue(normalized.contains("finance_cost_allocation_reversal"));
        }
    }

    @Test
    void legacyCostSubjectCleanupIsMirroredAndCoversEveryReferenceColumn() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V214__remove_legacy_cost_subjects.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V214__remove_legacy_cost_subjects.sql");
        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        List<String> referenceColumns = List.of(
                "cost_item set cost_subject_id",
                "cost_target_item set cost_subject_id",
                "cost_forecast_item set cost_subject_id",
                "project_budget_line set cost_subject_id",
                "pay_application set cost_subject_id",
                "expense_application set cost_subject_id",
                "stl_settlement_item set cost_subject_id",
                "accounting_entry_line set cost_subject_id",
                "cost_subject_assignment_rule set cost_subject_id",
                "project_cost_subject_scope set cost_subject_id",
                "qs_consequence set cost_subject_id",
                "finance_cost_allocation_batch set cost_subject_id",
                "bid_cost_target_transfer_line set source_subject_id",
                "bid_cost_target_transfer_line set target_subject_id",
                "cost_summary set cost_subject_id",
                "overhead_allocation_rule set cost_subject_id",
                "overhead_allocation_record set cost_subject_id",
                "var_order_item set cost_subject_id");

        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            String normalized = normalizeSql(sql);
            assertTrue(normalized.contains("create table cost_subject_legacy_cleanup_audit"));
            assertTrue(normalized.contains("'5001.01','分包成本-劳务',900043,'5401.03.01.02','劳务分包费'"));
            assertTrue(normalized.contains("delete from cost_subject_mapping_item where source_subject_id"));
            for (String referenceColumn : referenceColumns) {
                assertTrue(normalized.contains("update " + referenceColumn), referenceColumn);
            }
            assertTrue(normalized.contains("delete from cost_subject where id in"));
        }
    }

    @Test
    void localTestProfileIncludesJavaMigrationsWhenPresent() throws Exception {
        Path localProfile = Path.of("src/test/resources/application-local.yml");
        if (!Files.exists(localProfile)) {
            return;
        }
        String testLocal = Files.readString(localProfile);
        assertTrue(testLocal.contains("classpath:com/cgcpms/common/migration"));
    }

    @Test
    void v89CostSubjectSeedUsesCanonicalColumns() throws IOException {
        String sql = readString(MIGRATION_DIR.resolve("V89__fix_v78_seed_subjects.sql"));

        assertFalse(sql.contains("subject_level"));
        assertFalse(sql.contains("cost_type"));
        assertFalse(sql.contains("INSERT IGNORE INTO cost_subject (tenant_id"));
        assertTrue(sql.contains("subject_type"));
        assertTrue(sql.contains("level"));
    }

    @Test
    void costTargetIdColumnIsIntroducedOnlyOnceInMigrationChain() throws IOException {
        long additions = migrationFiles().stream()
                .map(MigrationIntegrityTest::readString)
                  .filter(sql -> DIRECT_ADD_COST_TARGET_ID.matcher(sql).find())
                .count();

        assertEquals(1, additions,
                "cost_summary.cost_target_id must not be added by multiple versioned migrations");
    }

    @Test
    void submitPermissionPatchUsesNewMigrationVersion() throws IOException {
        List<Path> permissionMigrations = migrationFiles().stream()
                .filter(path -> readString(path).contains("cost:target:submit"))
                .toList();

        assertTrue(permissionMigrations.stream()
                        .anyMatch(path -> path.getFileName().toString().startsWith("V32__")),
                "submit permission backfill must live in a new V32+ migration");
        assertFalse(permissionMigrations.stream()
                        .anyMatch(path -> path.getFileName().toString().startsWith("V21__")),
                "V21 is already in use in deployed databases and must not contain submit permission backfill");
    }

    @Test
    void matStockSoftDeleteUniqueConstraintRepairUsesNewMigrationVersion() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V82__fix_mat_stock_unique_with_deleted_flag.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V82__fix_mat_stock_unique_with_deleted_flag.sql");

        assertTrue(Files.exists(mysqlMigration),
                "MySQL mat_stock soft-delete unique repair must live in a new V82 migration");
        assertTrue(Files.exists(h2Migration),
                "H2 mat_stock soft-delete unique repair must live in a new V82 migration");

        String mysqlSql = readString(mysqlMigration);
        assertTrue(mysqlSql.contains("ALTER TABLE mat_stock DROP INDEX uk_ms_warehouse_material"));
        assertTrue(mysqlSql.contains("UNIQUE KEY uk_ms_warehouse_material (warehouse_id, material_id, deleted_flag)"));

        String h2Sql = readString(h2Migration);
        String h2JavaMigration = Files.readString(Path.of("src/main/java/com/cgcpms/common/migration/V83__repair_h2_anonymous_soft_delete_unique_constraints.java"));
        assertTrue(h2JavaMigration.contains("ALTER TABLE mat_stock ADD UNIQUE KEY uk_ms_warehouse_material (warehouse_id, material_id, deleted_flag)"));
        assertTrue(h2Sql.contains("H2 auto-generates the original UNIQUE constraint name"));
    }

    @Test
    void accountingEntryLineAuditColumnsAreBackfilledByNewMigrationVersion() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V82__fix_mat_stock_unique_with_deleted_flag.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V82__fix_mat_stock_unique_with_deleted_flag.sql");

        String mysqlSql = readString(mysqlMigration);
        assertTrue(mysqlSql.contains("ALTER TABLE accounting_entry_line ADD COLUMN created_by"));
        assertTrue(mysqlSql.contains("ALTER TABLE accounting_entry_line ADD COLUMN deleted_flag"));

        String h2Sql = readString(h2Migration);
        assertTrue(h2Sql.contains("ALTER TABLE accounting_entry_line ADD COLUMN IF NOT EXISTS created_by"));
        assertTrue(h2Sql.contains("ALTER TABLE accounting_entry_line ADD COLUMN IF NOT EXISTS deleted_flag"));
    }

    @Test
    void approvalWorkflowClosureDemoSeedUsesOnlyFirstRoundBusinessTypes() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V106__seed_approval_workflow_closure_demo_data.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V106__seed_approval_workflow_closure_demo_data.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            assertTrue(sql.contains("'CONTRACT_APPROVAL'"));
            assertTrue(sql.contains("'PURCHASE_REQUEST'"));
            assertTrue(sql.contains("'SUB_MEASURE'"));
            assertTrue(sql.contains("'RUNNING'"));
            assertTrue(sql.contains("'ACTIVE'"));
            assertTrue(sql.contains("'PENDING'"));
            assertTrue(sql.contains("'SUBMIT'"));
            assertTrue(sql.contains("wf_node_instance"));
            assertTrue(sql.contains("wf_task"));
            assertTrue(sql.contains("wf_record"));
            assertFalse(sql.contains("'CONTRACT'"),
                    "V106 must not use legacy CONTRACT as a replacement for CONTRACT_APPROVAL");
            assertFalse(sql.contains("'PAY_REQUEST'"));
            assertFalse(sql.contains("'PAY_APPLICATION'"));
            assertFalse(sql.contains("'VAR_ORDER'"));
            assertFalse(sql.contains("'CT_CHANGE'"));
            assertFalse(sql.contains("'TECH_ITEM'"));
        }
    }

    @Test
    void workflowMineEnhancementDemoSeedStaysOnPrimaryApprovalTypes() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V108__seed_workflow_mine_enhancement_demo_data.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V108__seed_workflow_mine_enhancement_demo_data.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            assertTrue(sql.contains("'CONTRACT_APPROVAL'"));
            assertTrue(sql.contains("'PURCHASE_REQUEST'"));
            assertTrue(sql.contains("'SUB_MEASURE'"));
            assertTrue(sql.contains("'RUNNING'"));
            assertTrue(sql.contains("'APPROVED'"));
            assertTrue(sql.contains("'REJECTED'"));
            assertTrue(sql.contains("'WITHDRAWN'"));
            assertTrue(sql.contains("'WITHDRAW'"));
            assertTrue(sql.contains("wf_instance"));
            assertTrue(sql.contains("wf_record"));
            assertTrue(sql.contains("wf_node_instance"));
            assertTrue(sql.contains("wf_task"));
            assertFalse(sql.contains("'CONTRACT'"),
                    "V108 must not use legacy CONTRACT as the primary sample type");
            assertFalse(sql.contains("'PAY_REQUEST'"));
            assertFalse(sql.contains("'PAY_APPLICATION'"));
            assertFalse(sql.contains("'VAR_ORDER'"));
            assertFalse(sql.contains("'CT_CHANGE'"));
            assertFalse(sql.contains("'TECH_ITEM'"));
        }
    }

    @Test
    void workflowCcDemoSeedOnlyTouchesCcRows() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V109__seed_workflow_cc_demo_data.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V109__seed_workflow_cc_demo_data.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            assertTrue(sql.contains("INSERT INTO wf_cc"));
            assertTrue(sql.contains("JOIN wf_instance"));
            assertTrue(sql.contains("979000000000000901"));
            assertTrue(sql.contains("979000000000000902"));
            assertTrue(sql.contains("978000000000001001"));
            assertTrue(sql.contains("978000000000001002"));
            assertFalse(sql.contains("INSERT INTO wf_instance"));
            assertFalse(sql.contains("INSERT INTO wf_task"));
            assertFalse(sql.contains("INSERT INTO wf_record"));
            assertFalse(sql.contains("INSERT INTO wf_node_instance"));
        }
    }

    @Test
    void approvalPermissionMatrixDemoSeedStaysWithinSeedBoundary() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V110__seed_approval_permission_matrix_demo_accounts.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V110__seed_approval_permission_matrix_demo_accounts.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            assertTrue(sql.contains("demo_workflow_only"));
            assertTrue(sql.contains("demo_cc_readonly"));
            assertTrue(sql.contains("demo_non_participant"));
            assertTrue(sql.contains("WORKFLOW_ONLY_DEMO"));
            assertTrue(sql.contains("CC_READONLY_DEMO"));
            assertTrue(sql.contains("NON_PARTICIPANT_DEMO"));
            assertTrue(sql.contains("'ALL'"));
            assertTrue(sql.contains("INSERT INTO sys_user"));
            assertTrue(sql.contains("INSERT INTO sys_role"));
            assertTrue(sql.contains("INSERT INTO sys_user_role"));
            assertTrue(sql.contains("INSERT INTO sys_role_menu"));
            assertTrue(sql.contains("INSERT INTO wf_instance"));
            assertTrue(sql.contains("INSERT INTO wf_cc"));
            assertTrue(sql.contains("908"));
            assertTrue(sql.contains("946"));
            assertTrue(sql.contains("947"));
            assertTrue(sql.contains("948"));
            assertTrue(sql.contains("949"));
            assertFalse(sql.contains("INSERT INTO sys_menu"));
            assertFalse(sql.contains("ALTER TABLE"));
            assertFalse(sql.contains("CREATE INDEX"));
            assertFalse(sql.contains("workflow:approve"));
            assertFalse(sql.contains("workflow:reject"));
            assertFalse(sql.contains("workflow:transfer"));
            assertFalse(sql.contains("workflow:add-sign"));
            assertFalse(sql.contains("workflow:withdraw"));
            assertFalse(sql.contains("workflow:resubmit"));
            assertFalse(sql.contains("contract:query"));
            assertFalse(sql.contains("purchase:request:list"));
            assertFalse(sql.contains("subcontract:measure:query"));
        }
    }

    @Test
    void approvalPermissionMatrixWorkflowOnlyRepairStaysWithinSeedBoundary() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V111__repair_approval_permission_matrix_workflow_only_samples.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V111__repair_approval_permission_matrix_workflow_only_samples.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            assertTrue(sql.contains("demo_workflow_only"));
            assertTrue(sql.contains("2071032241708793858"));
            assertTrue(sql.contains("970000000000005001"));
            assertTrue(sql.contains("CT-DEMO-WF-PERM-ONLY-001"));
            assertTrue(sql.contains("PR-DEMO-WF-PERM-ONLY-001"));
            assertTrue(sql.contains("SM-DEMO-WF-PERM-ONLY-001"));
            assertTrue(sql.contains("PR-DEMO-REAL-001"));
            assertTrue(sql.contains("SM-DEMO-REAL-001"));
            assertTrue(sql.contains("INSERT INTO ct_contract"));
            assertTrue(sql.contains("INSERT INTO mat_purchase_request"));
            assertTrue(sql.contains("INSERT INTO sub_measure"));
            assertTrue(sql.contains("INSERT INTO wf_instance"));
            assertTrue(sql.contains("INSERT INTO wf_node_instance"));
            assertTrue(sql.contains("INSERT INTO wf_task"));
            assertTrue(sql.contains("INSERT INTO wf_record"));
            assertFalse(sql.contains("INSERT INTO sys_user"));
            assertFalse(sql.contains("INSERT INTO sys_role"));
            assertFalse(sql.contains("INSERT INTO sys_user_role"));
            assertFalse(sql.contains("INSERT INTO sys_role_menu"));
            assertFalse(sql.contains("INSERT INTO sys_menu"));
            assertFalse(sql.contains("INSERT INTO wf_cc"));
            assertFalse(sql.contains("ALTER TABLE"));
            assertFalse(sql.contains("CREATE INDEX"));
            assertFalse(sql.contains("contract:query"));
            assertFalse(sql.contains("purchase:request:list"));
            assertFalse(sql.contains("subcontract:measure:query"));
        }
    }

    @Test
    void roleMenuAuditSnapshotMigrationStaysMinimalAndDedicated() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V112__create_role_menu_audit_snapshot.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V112__create_role_menu_audit_snapshot.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            assertTrue(sql.contains("CREATE TABLE sys_role_menu_audit_snapshot"));
            assertTrue(sql.contains("operator_id"));
            assertTrue(sql.contains("role_id"));
            assertTrue(sql.contains("before_menu_ids"));
            assertTrue(sql.contains("after_menu_ids"));
            assertTrue(sql.contains("success_flag"));
            assertTrue(sql.contains("error_summary"));
            assertFalse(sql.contains("CREATE TABLE sys_menu"));
            assertFalse(sql.contains("CREATE TABLE sys_role_menu ("));
            assertFalse(sql.contains("permission_registry"));
            assertFalse(sql.contains("entry_registry"));
        }
    }

    @Test
    void notificationListLegacyPermCleanupStaysPointedAndDoesNotRetargetAuthorization() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V113__clear_notification_list_legacy_perm.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V113__clear_notification_list_legacy_perm.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            assertTrue(sql.contains("UPDATE sys_menu"));
            assertTrue(sql.contains("id = 761"));
            assertTrue(sql.contains("perms = NULL"));
            assertTrue(sql.contains("perms = 'notification:list'"));
            assertFalse(sql.contains("notification:view"));
            assertFalse(sql.contains("UPDATE sys_role_menu"));
            assertFalse(sql.contains("INSERT INTO sys_role_menu"));
        }
    }

    @Test
    void partnerListLegacyPermCleanupStaysPointedAndDoesNotRetargetAuthorization() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V114__clear_partner_list_legacy_perm.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V114__clear_partner_list_legacy_perm.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            assertTrue(sql.contains("UPDATE sys_menu"));
            assertTrue(sql.contains("id = 401"));
            assertTrue(sql.contains("perms = NULL"));
            assertTrue(sql.contains("perms = 'partner:list'"));
            assertFalse(sql.contains("partner:query"));
            assertFalse(sql.contains("UPDATE sys_role_menu"));
            assertFalse(sql.contains("INSERT INTO sys_role_menu"));
        }
    }

    @Test
    void contractListLegacyPermCleanupStaysPointedAndDoesNotRetargetAuthorization() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V115__clear_contract_list_legacy_perm.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V115__clear_contract_list_legacy_perm.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            assertTrue(sql.contains("UPDATE sys_menu"));
            assertTrue(sql.contains("id = 301"));
            assertTrue(sql.contains("perms = NULL"));
            assertTrue(sql.contains("perms = 'contract:list'"));
            assertFalse(sql.contains("contract:query"));
            assertFalse(sql.contains("UPDATE sys_role_menu"));
            assertFalse(sql.contains("INSERT INTO sys_role_menu"));
        }
    }

    @Test
    void p01PermissionFixPackStaysMinimalAndDualTrack() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V128__p01_minimal_permission_fix_pack.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V128__p01_minimal_permission_fix_pack.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        assertP01PermissionFixPack(readString(mysqlMigration), true);
        assertP01PermissionFixPack(readString(h2Migration), false);
    }

    @Test
    void highRiskDbOnlyPermissionCleanupStaysPointedAndDoesNotTouchRoleBindings() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V129__clear_high_risk_db_only_permissions.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V129__clear_high_risk_db_only_permissions.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        for (String sql : List.of(readString(mysqlMigration), readString(h2Migration))) {
            assertHighRiskDbOnlyPermissionCleanup(sql);
        }
    }

    @Test
    void payRecordExternalTxnNoUniqueConstraintRemainsTenantScopedForMysqlAndH2() throws IOException {
        Path mysqlMigration = MIGRATION_DIR.resolve("V76__fix_pay_record_external_txn_no_unique.sql");
        Path h2Migration = H2_MIGRATION_DIR.resolve("V76__fix_pay_record_external_txn_no_unique.sql");

        assertTrue(Files.exists(mysqlMigration));
        assertTrue(Files.exists(h2Migration));

        String mysqlSql = readString(mysqlMigration);
        assertTrue(mysqlSql.contains("DROP INDEX uk_external_txn_no"));
        assertTrue(mysqlSql.contains("UNIQUE KEY uk_external_txn_no (tenant_id, external_txn_no, deleted_flag)"));

        String h2Sql = readString(h2Migration);
        assertTrue(h2Sql.contains("DROP INDEX uk_external_txn_no"));
        assertTrue(h2Sql.contains("UNIQUE KEY uk_external_txn_no (tenant_id, external_txn_no, deleted_flag)"));
    }

    /**
     * Java-based migrations (V51/V58/V75) are executed by Flyway at startup
     * from {@code common/migration/} and do not require SQL-level coverage here —
     * they apply soft-delete-safe unique constraints and index repairs that are
     * verified at runtime rather than via static file analysis.
     */
    // V51/V58/V75 已转为 Java 迁移 (common/migration/)，H2 下由 Flyway 自动执行。无需额外 SQL 验证。

    private static List<Path> migrationFiles() throws IOException {
        try (var stream = Files.list(MIGRATION_DIR)) {
            return stream
                    .filter(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted()
                    .toList();
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read migration " + path, e);
        }
    }

    private static void assertP01PermissionFixPack(String sql, boolean mysql) {
        String normalizedSql = normalizeSql(sql);
        Set<String> bindings = roleMenuBindings(normalizedSql);
        assertTrue(normalizedSql.contains("id = 751"));
        assertTrue(normalizedSql.contains("perms = 'invoice:list'"));
        assertTrue(normalizedSql.contains("id = 201"));
        assertTrue(normalizedSql.contains("perms = 'project:list'"));
        assertTrue(normalizedSql.contains("'requisition:submit'"));
        assertTrue(normalizedSql.contains("role_id"));
        assertTrue(normalizedSql.contains("menu_id"));
        assertTrue(bindings.contains("(1,917)"),
                "V128 must backfill role/menu binding (1,917) for " + (mysql ? "MySQL" : "H2"));
        assertTrue(bindings.contains("(1,918)"),
                "V128 must backfill role/menu binding (1,918) for " + (mysql ? "MySQL" : "H2"));
        assertTrue(bindings.contains("(5,917)"),
                "V128 must backfill role/menu binding (5,917) for " + (mysql ? "MySQL" : "H2"));
        assertTrue(bindings.contains("(5,918)"),
                "V128 must backfill role/menu binding (5,918) for " + (mysql ? "MySQL" : "H2"));
        assertFalse(normalizedSql.contains("project:delete"));
        assertFalse(normalizedSql.contains("update sys_role_menu"));
        assertFalse(normalizedSql.contains("delete from sys_role_menu"));
    }

    private static void assertHighRiskDbOnlyPermissionCleanup(String sql) {
        String normalizedSql = normalizeSql(sql);
        assertEquals(4, countOccurrences(normalizedSql, "update sys_menu set perms = null"),
                "V129 must only clear the four approved high-risk DB-only permission codes");
        assertTrue(normalizedSql.contains("where id = 204 and perms = 'project:delete'"));
        assertTrue(normalizedSql.contains("where id = 501 and perms = 'system:user:list'"));
        assertTrue(normalizedSql.contains("where id = 502 and perms = 'system:role:list'"));
        assertTrue(normalizedSql.contains("where id = 503 and perms = 'system:menu:list'"));
        assertFalse(normalizedSql.contains("delete from sys_menu"));
        assertFalse(normalizedSql.contains("delete from sys_role_menu"));
        assertFalse(normalizedSql.contains("update sys_role_menu"));
        assertFalse(normalizedSql.contains("insert into sys_role_menu"));
    }

    private static String normalizeSql(String sql) {
        return sql.toLowerCase()
                .replaceAll("\\s+", " ")
                .replace("( ", "(")
                .replace(" )", ")")
                .replace(", ", ",");
    }

    private static Set<String> roleMenuBindings(String normalizedSql) {
        Pattern valuesPattern = Pattern.compile("\\(\\d+,(\\d+),(\\d+)\\)");
        Pattern selectPattern = Pattern.compile("select\\s+\\d+,(\\d+),(\\d+)\\s+where not exists");
        Set<String> bindings = new LinkedHashSet<>();
        var valuesMatcher = valuesPattern.matcher(normalizedSql);
        while (valuesMatcher.find()) {
            bindings.add("(" + valuesMatcher.group(1) + "," + valuesMatcher.group(2) + ")");
        }
        var selectMatcher = selectPattern.matcher(normalizedSql);
        while (selectMatcher.find()) {
            bindings.add("(" + selectMatcher.group(1) + "," + selectMatcher.group(2) + ")");
        }
        return bindings;
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
