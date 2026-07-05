package com.cgcpms.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationVersionUniquenessTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Test
    void migrationVersionsAreUniquePerDialect() throws IOException {
        assertUniqueVersions(Path.of("src/main/resources/db/migration"));
        assertUniqueVersions(Path.of("src/main/resources/db/migration-h2"));
    }

    private static void assertUniqueVersions(Path dir) throws IOException {
        Map<String, List<String>> filesByVersion = new HashMap<>();
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(path -> Files.isRegularFile(path))
                    .map(path -> path.getFileName().toString())
                    .forEach(fileName -> {
                        Matcher matcher = VERSION_PATTERN.matcher(fileName);
                        if (matcher.matches()) {
                            filesByVersion.merge(matcher.group(1), List.of(fileName), (left, right) -> Stream
                                    .concat(left.stream(), right.stream())
                                    .toList());
                        }
                    });
        }

        List<String> duplicates = filesByVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> dir + " V" + entry.getKey() + " -> " + entry.getValue())
                .toList();

        assertTrue(duplicates.isEmpty(), "Flyway migration versions must be unique: " + duplicates);
    }
}
