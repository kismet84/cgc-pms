package com.cgcpms;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationConfigUnitTest {

    @Test
    void serverPortShouldUseEnvironmentPlaceholder() throws Exception {
        var content = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(content.contains("  port: ${SERVER_PORT:8080}"));
    }
}
