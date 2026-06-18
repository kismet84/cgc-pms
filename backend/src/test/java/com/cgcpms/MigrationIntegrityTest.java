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
    private static final Pattern DIRECT_ADD_COST_TARGET_ID = Pattern.compile(
            "(?im)^\\s*ALTER\\s+TABLE\\s+cost_summary\\s*\\R\\s*ADD\\s+COLUMN\\s+cost_target_id");

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
