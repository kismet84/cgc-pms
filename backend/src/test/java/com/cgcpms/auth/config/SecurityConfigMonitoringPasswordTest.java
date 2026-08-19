package com.cgcpms.auth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigMonitoringPasswordTest {

    @TempDir
    Path tempDir;

    @Test
    void readsMonitoringPasswordFromMountedSecretFile() throws Exception {
        Path passwordFile = tempDir.resolve("monitoring-password");
        Files.writeString(passwordFile, "file-secret\n");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        SecurityConfig config = config("inline-secret", passwordFile.toString());

        UserDetails user = config.monitoringUserDetailsService(encoder)
                .loadUserByUsername("cgcpms-monitor");

        assertTrue(encoder.matches("file-secret", user.getPassword()));
        assertTrue(user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_MONITORING")));
    }

    @Test
    void failsClosedWhenConfiguredSecretFileCannotBeRead() {
        Path missingFile = tempDir.resolve("missing-password");
        SecurityConfig config = config("inline-secret", missingFile.toString());

        assertThrows(IllegalStateException.class,
                () -> config.monitoringUserDetailsService(new BCryptPasswordEncoder()));
    }

    @Test
    void rejectsInlineMonitoringPasswordOutsideTestAndLocalProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        SecurityConfig config = new SecurityConfig(
                null,
                null,
                environment,
                false,
                true,
                "cgcpms-monitor",
                "inline-secret",
                "");

        assertThrows(IllegalStateException.class,
                () -> config.monitoringUserDetailsService(new BCryptPasswordEncoder()));
    }

    private SecurityConfig config(String inlinePassword, String passwordFile) {
        return new SecurityConfig(
                null,
                null,
                new MockEnvironment().withProperty("spring.profiles.active", "local"),
                false,
                true,
                "cgcpms-monitor",
                inlinePassword,
                passwordFile);
    }
}
