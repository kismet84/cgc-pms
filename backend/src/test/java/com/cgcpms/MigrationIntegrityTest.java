package com.cgcpms;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    void localTestProfileIncludesJavaMigrations() throws Exception {
        String testLocal = Files.readString(Path.of("src/test/resources/application-local.yml"));
        assertTrue(testLocal.contains("classpath:com/cgcpms/common/migration"));
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
}
