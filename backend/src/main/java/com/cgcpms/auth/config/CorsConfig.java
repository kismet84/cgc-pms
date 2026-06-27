package com.cgcpms.auth.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS configuration. Allowed origins are driven by the
 * {@code cors.allowed-origins} property so each profile can declare
 * its own set without code changes.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @PostConstruct
    void validateOrigins() {
        List<String> normalized = normalizeAllowedOrigins();
        for (String origin : normalized) {
            if ("*".equals(origin)) {
                throw new IllegalStateException("cors.allowed-origins 不允许使用 '*'");
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalStateException("cors.allowed-origins不能为空");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] configuredOrigins = normalizeAllowedOrigins().toArray(new String[0]);
        registry.addMapping("/**")
                .allowedOrigins(configuredOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Refresh-Token")
                .allowCredentials(true);
    }

    private List<String> normalizeAllowedOrigins() {
        if (allowedOrigins == null || allowedOrigins.length == 0) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String origin : allowedOrigins) {
            if (origin == null) {
                continue;
            }
            String trimmed = origin.trim();
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

}
