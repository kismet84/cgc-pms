package com.cgcpms.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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

        assertEquals("231", flyway.info().current().getVersion().getVersion());
        assertTrue(Arrays.stream(flyway.info().applied())
                .anyMatch(info -> info.getType().name().contains("BASELINE")));
        assertEquals(12, count(flyway, "sys_role"));
        assertEquals(0, count(flyway, "sys_user"));
        assertEquals(0, count(flyway, "pm_project"));
        assertEquals(0, count(flyway, "md_material"));
        assertEquals(0, count(flyway, "mat_stock"));
        assertEquals(0, count(flyway, "wf_instance"));
        assertEquals(1, count(flyway, "sys_bootstrap_state"));
        assertEquals(1, count(flyway, "sys_menu", "perms='payment:direct'"));
        assertEquals(1, count(flyway, "sys_role_menu",
                "role_id=1 AND menu_id=(SELECT id FROM sys_menu WHERE perms='payment:direct')"));
        assertEquals(0, count(flyway, "sys_role_menu",
                "role_id=6 AND menu_id=(SELECT id FROM sys_menu WHERE perms='payment:direct')"));
        assertEquals(1, count(flyway, "sys_role", "role_code='COST_MANAGER'"));
        assertEquals(1, count(flyway, "sys_role", "role_code='DEPARTMENT_MANAGER'"));
        assertEquals(1, count(flyway, "sys_role", "role_code='GENERAL_MANAGER'"));
        assertEquals(1, count(flyway, "wf_template_node",
                "id=50501 AND approver_config LIKE '%PROJECT_MANAGER%'"));
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

        assertEquals("231", current.info().current().getVersion().getVersion());
        assertFalse(Arrays.stream(current.info().applied())
                .anyMatch(info -> info.getType().name().contains("BASELINE")));
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

    private static void execute(Flyway flyway, String sql) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare migration fixture", exception);
        }
    }
}
