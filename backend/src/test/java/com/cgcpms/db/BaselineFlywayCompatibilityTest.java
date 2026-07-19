package com.cgcpms.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineFlywayCompatibilityTest {

    private static final String ACTIVE = "classpath:db/migration-h2";
    private static final String LEGACY = "filesystem:src/main/resources/db/migration-h2-legacy";
    private static final String JAVA = "classpath:com/cgcpms/common/migration";

    @Test
    void freshH2DatabaseUsesB215AndContainsNoBusinessDemoFacts() {
        Flyway flyway = flyway("fresh", ACTIVE, LEGACY, JAVA);
        flyway.migrate();

        assertEquals("217", flyway.info().current().getVersion().getVersion());
        assertTrue(Arrays.stream(flyway.info().applied())
                .anyMatch(info -> info.getType().name().contains("BASELINE")));
        assertEquals(9, count(flyway, "sys_role"));
        assertEquals(0, count(flyway, "sys_user"));
        assertEquals(0, count(flyway, "pm_project"));
        assertEquals(0, count(flyway, "md_material"));
        assertEquals(0, count(flyway, "mat_stock"));
        assertEquals(0, count(flyway, "wf_instance"));
        assertEquals(1, count(flyway, "sys_bootstrap_state"));
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

        assertEquals("217", current.info().current().getVersion().getVersion());
        assertFalse(Arrays.stream(current.info().applied())
                .anyMatch(info -> info.getType().name().contains("BASELINE")));
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
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to count " + table, exception);
        }
    }
}
