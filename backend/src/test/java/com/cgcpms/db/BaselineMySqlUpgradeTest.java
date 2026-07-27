package com.cgcpms.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "CGCPMS_M52_MYSQL_UPGRADE", matches = "true")
class BaselineMySqlUpgradeTest {

    private static final String LEGACY = "classpath:db/migration-legacy";
    private static final String ACTIVE = "classpath:db/migration";

    @Test
    void existingV180MySqlIgnoresBaselineAndUpgradesToLatestVersion() {
        String url = required("SPRING_DATASOURCE_URL");
        String username = required("SPRING_DATASOURCE_USERNAME");
        String password = required("SPRING_DATASOURCE_PASSWORD");

        Flyway old = Flyway.configure()
                .dataSource(url, username, password)
                .locations(LEGACY)
                .target(MigrationVersion.fromVersion("180"))
                .load();
        old.migrate();
        assertEquals("180", old.info().current().getVersion().getVersion());

        Flyway current = Flyway.configure()
                .dataSource(url, username, password)
                .locations(ACTIVE, LEGACY)
                .load();
        current.migrate();

        assertEquals("234", current.info().current().getVersion().getVersion());
        assertEquals(5, count(current, """
                SELECT COUNT(*) FROM sys_role_menu rm
                JOIN sys_role r ON r.tenant_id=rm.tenant_id AND r.id=rm.role_id
                JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
                WHERE r.role_code IN
                  ('PROJECT_MANAGER','COST_MANAGER','DEPARTMENT_MANAGER','GENERAL_MANAGER','FINANCE')
                  AND r.deleted_flag=0 AND m.deleted_flag=0 AND m.perms='project:query'
                """));
        assertEquals(1, count(current, """
                SELECT COUNT(*) FROM sys_role_menu rm
                JOIN sys_role r ON r.tenant_id=rm.tenant_id AND r.id=rm.role_id
                JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
                WHERE r.role_code='PROJECT_MANAGER' AND r.deleted_flag=0
                  AND m.deleted_flag=0 AND m.perms='workflow:resubmit'
                """));
        assertEquals(1, count(current, """
                SELECT COUNT(*) FROM sys_role_menu rm
                JOIN sys_role r ON r.tenant_id=rm.tenant_id AND r.id=rm.role_id
                JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
                WHERE r.role_code='SUPER_ADMIN' AND r.deleted_flag=0
                  AND m.deleted_flag=0 AND m.perms='audit:query'
                """));
        var validation = current.validateWithResult();
        assertTrue(validation.validationSuccessful, String.join("\n", validation.getAllErrorMessages()));
        assertFalse(Arrays.stream(current.info().applied())
                .anyMatch(info -> info.getType().name().contains("BASELINE")));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + "_REQUIRED");
        }
        return value;
    }

    private static int count(Flyway flyway, String sql) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to verify upgraded role permissions", exception);
        }
    }
}
