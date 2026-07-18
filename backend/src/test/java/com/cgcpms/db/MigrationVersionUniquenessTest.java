package com.cgcpms.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationVersionUniquenessTest {

    private static final Pattern SQL_VERSION = Pattern.compile("^([BV])(\\d+)__.+\\.sql$");
    private static final Pattern JAVA_VERSION = Pattern.compile("^V(\\d+)__.+\\.java$");

    @Test
    void migrationVersionsAreUniqueAcrossActiveLegacyAndJavaLocations() throws IOException {
        assertUniqueVersions("mysql", List.of(
                Path.of("src/main/resources/db/migration"),
                Path.of("src/main/resources/db/migration-legacy")), false);
        assertUniqueVersions("h2", List.of(
                Path.of("src/main/resources/db/migration-h2"),
                Path.of("src/main/resources/db/migration-h2-legacy"),
                Path.of("src/main/java/com/cgcpms/common/migration")), true);
    }

    private static void assertUniqueVersions(String dialect, List<Path> directories, boolean includeJava)
            throws IOException {
        Map<String, List<String>> filesByTypeAndVersion = new HashMap<>();
        for (Path directory : directories) {
            try (Stream<Path> paths = Files.list(directory)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    String name = path.getFileName().toString();
                    Matcher sql = SQL_VERSION.matcher(name);
                    Matcher java = JAVA_VERSION.matcher(name);
                    String key = null;
                    if (sql.matches()) {
                        key = sql.group(1) + sql.group(2);
                    } else if (includeJava && java.matches()) {
                        key = "V" + java.group(1);
                    }
                    if (key != null) {
                        filesByTypeAndVersion.computeIfAbsent(key, ignored -> new ArrayList<>())
                                .add(path.toString());
                    }
                }
            }
        }

        List<String> duplicates = filesByTypeAndVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> dialect + " " + entry.getKey() + " -> " + entry.getValue())
                .toList();
        assertTrue(duplicates.isEmpty(), "Flyway migration versions must be unique: " + duplicates);
    }
}
