package com.cgcpms.alert;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertPermissionMigrationTest {

    private static final String FILE_NAME = "V146__split_alert_evaluate_permission.sql";

    @Test
    void migrationSplitsOnlyBatchEvaluatePermissionForMysqlAndH2() throws IOException {
        assertMigration(Path.of("src/main/resources/db/migration-legacy", FILE_NAME));
        assertMigration(Path.of("src/main/resources/db/migration-h2-legacy", FILE_NAME));
    }

    private void assertMigration(Path migration) throws IOException {
        assertTrue(Files.exists(migration), () -> "Missing migration: " + migration);
        String sql = Files.readString(migration).replaceAll("\\s+", " ").trim();
        assertTrue(sql.contains("UPDATE sys_menu"));
        assertTrue(sql.contains("perms = 'alert:evaluate'"));
        assertTrue(sql.contains("id = 768"));
        assertTrue(sql.contains("perms = 'alert:edit'"));
        assertFalse(sql.contains("id = 767"));
        assertFalse(sql.toUpperCase().contains("DELETE "));
        assertFalse(sql.toUpperCase().contains("INSERT "));
    }
}
