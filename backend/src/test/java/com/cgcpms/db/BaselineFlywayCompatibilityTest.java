package com.cgcpms.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineFlywayCompatibilityTest {

    private static final String ACTIVE = "classpath:db/migration-h2";
    private static final String LEGACY = "filesystem:src/main/resources/db/migration-h2-legacy";
    private static final String JAVA = "classpath:com/cgcpms/common/migration";

    @Test
    void freshH2DatabaseUsesB215AndContainsNoBusinessDemoFacts() {
        Flyway flyway = flyway("fresh", ACTIVE, LEGACY, JAVA);
        flyway.migrate();

        assertEquals("272", flyway.info().current().getVersion().getVersion());
        assertEquals(10, count(flyway, "cost_subject", "parent_id=(SELECT id FROM cost_subject WHERE subject_code='5401.03')"));
        assertEquals(0, count(flyway, "cost_subject", "subject_code='5401.02' OR subject_code LIKE '5401.02.%'"));
        assertEquals(21, count(flyway, "cost_subject", """
                subject_code='5401.01' OR subject_code LIKE '5401.01.%'
                OR subject_code='5401.04' OR subject_code LIKE '5401.04.%'
                """));
        execute(flyway, """
                INSERT INTO mat_warehouse
                    (id, tenant_id, project_id, warehouse_code, warehouse_name, status, deleted_flag)
                VALUES (235001, 1, 235010, 'WH-20260728-001', '仓库一', 'ENABLE', 0)
                """);
        assertThrows(IllegalStateException.class, () -> execute(flyway, """
                INSERT INTO mat_warehouse
                    (id, tenant_id, project_id, warehouse_code, warehouse_name, status, deleted_flag)
                VALUES (235002, 1, 235010, 'WH-20260728-001', '仓库二', 'ENABLE', 0)
                """));
        assertTrue(Arrays.stream(flyway.info().applied())
                .anyMatch(info -> info.getType().name().contains("BASELINE")));
        assertEquals(13, count(flyway, "sys_role"));
        assertEquals(0, count(flyway, "sys_user"));
        assertEquals(0, count(flyway, "pm_project"));
        assertEquals(0, count(flyway, "md_material"));
        assertEquals(0, count(flyway, "mat_stock"));
        assertEquals(0, count(flyway, "wf_instance"));
        assertEquals(1, count(flyway, "sys_bootstrap_state"));
        assertEquals(1, count(flyway, "sys_menu", "perms='payment:direct'"));
        assertEquals(1, count(flyway, "sys_role_menu",
                "role_id=1 AND menu_id=(SELECT id FROM sys_menu WHERE perms='payment:direct')"));
        assertEquals(1, count(flyway, "sys_role_menu",
                "role_id=1 AND menu_id=(SELECT id FROM sys_menu WHERE perms='audit:query')"));
        assertEquals(0, count(flyway, "sys_role_menu",
                "role_id=6 AND menu_id=(SELECT id FROM sys_menu WHERE perms='payment:direct')"));
        assertEquals(1, count(flyway, "sys_role", "role_code='COST_MANAGER'"));
        assertEquals(1, count(flyway, "sys_role", "role_code='DEPARTMENT_MANAGER'"));
        assertEquals(1, count(flyway, "sys_role", "role_code='GENERAL_MANAGER'"));
        assertEquals(1, count(flyway, "wf_template_node",
                "id=50501 AND approver_config LIKE '%PROJECT_ROLE%' AND approver_config LIKE '%PM%'"));
        assertEquals(1, count(flyway, "wf_template_node",
                "id=53001 AND approver_config LIKE '%GENERAL_MANAGER%' AND approve_mode='OR_SIGN'"));
        assertEquals(4, count(flyway, "wf_template_node",
                "id IN (50102,50402,50502,50802) AND approver_config LIKE '%DEPARTMENT_MANAGER%'"));
        assertEquals(0, count(flyway, "wf_template_node",
                "approver_config LIKE '%MANAGEMENT_EXECUTIVE%'"));
        assertEquals(9, count(flyway, "wf_template_node",
                "id IN (50501,50502,50503,52001,52002,52003,52101,52102,52103)"
                        + " AND approve_mode='OR_SIGN'"));
        assertEquals(3, count(flyway, "wf_template_node",
                "id IN (50101,50102,50103) AND approver_config LIKE '%roleCode%'"
                        + " AND approve_mode='OR_SIGN'"));
        assertEquals(1, count(flyway, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='COST_MANAGER')
                AND menu_id=(SELECT id FROM sys_menu WHERE perms='workflow:approve')
                """));
        assertEquals(2, count(flyway, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='DEPARTMENT_MANAGER')
                AND menu_id IN (SELECT id FROM sys_menu WHERE perms IN ('workflow:approve','workflow:reject'))
                """));
        assertEquals(1, count(flyway, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='PROJECT_MANAGER')
                AND menu_id=(SELECT id FROM sys_menu WHERE perms='payment:app:submit')
                """));
        assertEquals(2, count(flyway, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='PROJECT_MANAGER')
                AND menu_id IN (SELECT id FROM sys_menu
                    WHERE perms IN ('payment:app:add','payment:app:edit'))
                """));
        assertEquals(1, count(flyway, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='FINANCE')
                AND menu_id=(SELECT id FROM sys_menu WHERE perms='payment:record:writeback')
                """));
        assertEquals(5, count(flyway, "sys_role_menu", """
                role_id IN (SELECT id FROM sys_role WHERE role_code IN
                    ('PROJECT_MANAGER','COST_MANAGER','DEPARTMENT_MANAGER','GENERAL_MANAGER','FINANCE'))
                AND menu_id=(SELECT id FROM sys_menu WHERE perms='project:query')
                """));
        assertEquals(1, count(flyway, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='PROJECT_MANAGER')
                AND menu_id=(SELECT id FROM sys_menu WHERE perms='workflow:resubmit')
                """));
    }

    @Test
    void existingV180H2DatabaseIgnoresBaselineAndUpgradesThroughLegacyChain() {
        Flyway old = Flyway.configure()
                .dataSource(url("upgrade"), "sa", "")
                .locations(LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("180"))
                .cleanDisabled(false)
                .load();
        old.migrate();
        assertEquals("180", old.info().current().getVersion().getVersion());

        Flyway current = flyway("upgrade", ACTIVE, LEGACY, JAVA);
        current.migrate();
        var validation = current.validateWithResult();
        assertTrue(validation.validationSuccessful, String.join("\n", validation.getAllErrorMessages()));

        assertEquals("272", current.info().current().getVersion().getVersion());
        assertEquals(5, count(current, "sys_role_menu", """
                role_id IN (SELECT id FROM sys_role WHERE role_code IN
                    ('PROJECT_MANAGER','COST_MANAGER','DEPARTMENT_MANAGER','GENERAL_MANAGER','FINANCE'))
                AND menu_id=(SELECT id FROM sys_menu WHERE perms='project:query')
                """));
        assertEquals(1, count(current, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='PROJECT_MANAGER')
                AND menu_id=(SELECT id FROM sys_menu WHERE perms='workflow:resubmit')
                """));
        assertEquals(1, count(current, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='SUPER_ADMIN')
                AND menu_id=(SELECT id FROM sys_menu WHERE perms='audit:query')
                """));
        assertFalse(Arrays.stream(current.info().applied())
                .anyMatch(info -> info.getType().name().contains("BASELINE")));
    }

    @Test
    void v271H2RejectsNegativeTenderAmountsAndInvalidInitiationBasis() {
        Flyway flyway = Flyway.configure()
                .dataSource(url("v271_checks"), "sa", "")
                .locations(ACTIVE, LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("271"))
                .cleanDisabled(false)
                .load();
        flyway.migrate();

        execute(flyway, """
                INSERT INTO bid_cost
                    (id, tenant_id, bid_code, bid_project_name, bid_status, ceiling_price, final_bid_price)
                VALUES (271001, 271, 'BID-V271-001', 'V271约束测试投标', 'PREPARING', 100, 90)
                """);
        assertThrows(IllegalStateException.class,
                () -> execute(flyway, "UPDATE bid_cost SET ceiling_price=-0.01 WHERE id=271001"));
        assertThrows(IllegalStateException.class,
                () -> execute(flyway, "UPDATE bid_cost SET final_bid_price=-0.01 WHERE id=271001"));

        execute(flyway, """
                INSERT INTO pm_project
                    (id, tenant_id, project_code, project_name, initiation_basis)
                VALUES (271002, 271, 'V271-CHECK', 'V271约束测试项目', 'BID_AWARD')
                """);
        assertThrows(IllegalStateException.class,
                () -> execute(flyway, "UPDATE pm_project SET initiation_basis='MANUAL' WHERE id=271002"));
    }

    @Test
    void paymentRelationOrphanBlocksV226Upgrade() {
        Flyway old = Flyway.configure()
                .dataSource(url("payment_orphan"), "sa", "")
                .locations(LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("225"))
                .cleanDisabled(false)
                .load();
        old.migrate();
        execute(old, "SET REFERENTIAL_INTEGRITY FALSE");
        execute(old, """
                INSERT INTO invoice_payment_allocation
                    (id, tenant_id, invoice_id, pay_record_id, pay_application_id, allocated_amount)
                VALUES (226001, 1, 226002, 226003, 226004, 1.00)
                """);
        execute(old, "SET REFERENTIAL_INTEGRITY TRUE");

        Flyway current = flyway("payment_orphan", ACTIVE, LEGACY, JAVA);

        assertThrows(FlywayException.class, current::migrate);
    }

    @Test
    void v251AndV252RemoveUnusedIdempotencyColumnsAndReconcileContractCaches() {
        Flyway old = Flyway.configure()
                .dataSource(url("field_cleanup"), "sa", "")
                .locations(ACTIVE, LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("250"))
                .cleanDisabled(false)
                .load();
        old.migrate();
        execute(old, "SET REFERENTIAL_INTEGRITY FALSE");
        execute(old, """
                INSERT INTO ct_contract
                    (id, tenant_id, project_id, contract_code, contract_name, contract_type,
                     party_a_id, party_b_id, contract_amount, current_amount, paid_amount, deleted_flag)
                VALUES (251001, 7, 251010, 'CT-V252', 'V252合同', 'PURCHASE',
                        251011, 251012, 100, 1, 99, 0)
                """);
        execute(old, """
                INSERT INTO ct_contract_change
                    (id, tenant_id, project_id, contract_id, change_code, change_name, change_type,
                     before_amount, change_amount, after_amount, approval_status, effective_flag, deleted_flag)
                VALUES (251002, 7, 251010, 251001, 'CH-V252', 'V252变更', 'AMOUNT',
                        100, 20, 120, 'APPROVED', 1, 0)
                """);
        execute(old, """
                INSERT INTO ct_contract_change
                    (id, tenant_id, project_id, contract_id, change_code, change_name, change_type,
                     before_amount, change_amount, after_amount, approval_status, effective_flag, deleted_flag)
                VALUES (251004, 7, 251010, 251001, 'CH-V252-PENDING', '未生效变更', 'AMOUNT',
                        120, 10, 130, 'APPROVED', 0, 0)
                """);
        execute(old, """
                INSERT INTO pay_record
                    (id, tenant_id, project_id, pay_application_id, contract_id,
                     pay_amount, pay_date, pay_status, deleted_flag)
                VALUES (251003, 7, 251010, 251020, 251001, 30, CURRENT_DATE, 'SUCCESS', 0)
                """);
        execute(old, "SET REFERENTIAL_INTEGRITY TRUE");

        Flyway current = flyway("field_cleanup", ACTIVE, LEGACY, JAVA);
        current.migrate();

        assertEquals(1, count(current, "ct_contract", "id=251001 AND current_amount=120 AND paid_amount=30"));
        assertEquals(0, count(current, "information_schema.columns", "table_name='wf_idempotency'"
                + " AND column_name IN ('business_type','business_id','request_hash','response_json')"));
        assertEquals(1, count(current, "information_schema.indexes", "table_name='mat_purchase_order'"
                + " AND index_name='idx_purchase_order_request_source'"));
    }

    @Test
    void v236AlignsNavigationWithoutChangingRolePermissions() {
        Flyway old = Flyway.configure()
                .dataSource(url("menu_alignment"), "sa", "")
                .locations(ACTIVE, LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("235"))
                .cleanDisabled(false)
                .load();
        old.migrate();
        List<String> roleMenus = rows(old, """
                SELECT CONCAT(tenant_id, ':', role_id, ':', menu_id)
                FROM sys_role_menu
                WHERE role_id<>2690001 AND menu_id NOT IN (2690100,2690101,2690111,2690112,2690113)
                ORDER BY tenant_id, role_id, menu_id
                """);
        List<String> rolePermissions = rows(old, """
                SELECT DISTINCT CONCAT(rm.tenant_id, ':', rm.role_id, ':', m.perms)
                FROM sys_role_menu rm
                JOIN sys_menu m ON m.tenant_id = rm.tenant_id AND m.id = rm.menu_id
                WHERE m.perms IS NOT NULL AND m.perms <> ''
                  AND rm.role_id<>2690001 AND rm.menu_id NOT IN (2690100,2690101,2690111,2690112,2690113)
                ORDER BY 1
                """);

        Flyway current = flyway("menu_alignment", ACTIVE, LEGACY, JAVA);
        current.migrate();

        assertEquals(roleMenus, rows(current, """
                SELECT CONCAT(tenant_id, ':', role_id, ':', menu_id)
                FROM sys_role_menu
                WHERE role_id<>2690001 AND menu_id NOT IN (2690100,2690101,2690111,2690112,2690113)
                ORDER BY tenant_id, role_id, menu_id
                """));
        assertEquals(rolePermissions, rows(current, """
                SELECT DISTINCT CONCAT(rm.tenant_id, ':', rm.role_id, ':', m.perms)
                FROM sys_role_menu rm
                JOIN sys_menu m ON m.tenant_id = rm.tenant_id AND m.id = rm.menu_id
                WHERE m.perms IS NOT NULL AND m.perms <> ''
                  AND rm.role_id<>2690001 AND rm.menu_id NOT IN (2690100,2690101,2690111,2690112,2690113)
                ORDER BY 1
                """));
        assertEquals(2, count(current, "sys_menu",
                "id IN (23601,23602) AND perms IS NULL AND menu_type='MENU'"));
        assertEquals(0, count(current, "sys_role_menu", "menu_id IN (23601,23602)"));
        assertEquals(1, count(current, "sys_menu",
                "id=503 AND parent_id=909 AND path='/system/permissions'"));
        assertEquals(3, count(current, "sys_menu",
                "id IN (2134,2138,2139) AND menu_type='MENU' AND path IS NOT NULL"));
    }

    private static Flyway flyway(String name, String... locations) {
        return Flyway.configure()
                .dataSource(url(name), "sa", "")
                .locations(locations)
                .cleanDisabled(false)
                .load();
    }

    private static String url(String name) {
        return "jdbc:h2:mem:cgc_m52_" + name
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    }

    private static int count(Flyway flyway, String table) {
        return count(flyway, table, null);
    }

    private static int count(Flyway flyway, String table, String where) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table
                     + (where == null ? "" : " WHERE " + where))) {
            result.next();
            return result.getInt(1);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to count " + table, exception);
        }
    }

    private static List<String> rows(Flyway flyway, String sql) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            var rows = new ArrayList<String>();
            while (result.next()) rows.add(result.getString(1));
            return rows;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to query migration facts", exception);
        }
    }

    private static void execute(Flyway flyway, String sql) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare migration fixture", exception);
        }
    }
}
