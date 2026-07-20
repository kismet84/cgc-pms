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

        assertEquals("218", current.info().current().getVersion().getVersion());
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
}
