package com.cgcpms.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CORS 预检测试")
class CorsConfigTest {

    @Test
    @DisplayName("凭据 CORS 允许 CSRF 头且拒绝未知 origin")
    void credentialedCorsAllowsXsrfHeaderAndRejectsUnknownOrigin() {
        var configurer = new CorsConfig();
        ReflectionTestUtils.setField(configurer, "allowedOrigins", new String[]{" http://localhost:5173 "});
        configurer.validateOrigins();
        var registry = new ExposedCorsRegistry();
        configurer.addCorsMappings(registry);

        CorsConfiguration cors = registry.configurations().get("/**");
        assertEquals("http://localhost:5173", cors.checkOrigin("http://localhost:5173"));
        assertNull(cors.checkOrigin("https://unknown.example"));
        assertTrue(Boolean.TRUE.equals(cors.getAllowCredentials()));
        assertTrue(cors.getAllowedHeaders().contains("X-XSRF-TOKEN"));
    }

    private static final class ExposedCorsRegistry extends CorsRegistry {
        private Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
