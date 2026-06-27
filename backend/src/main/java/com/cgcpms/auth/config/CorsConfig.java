package com.cgcpms.auth.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration. Allowed origins are driven by the
 * {@code cors.allowed-origins} property so each profile can declare
 * its own set without code changes.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    private final Environment environment;
    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateOrigins() {
        List<String> normalized = normalizeAllowedOrigins();
        for (String origin : normalized) {
            if ("*".equals(origin)) {
                if (!isLocalProfile()) {
                    throw new IllegalStateException("生产环境不允许 CORS 使用 wildcard（*）");
                }
                log.warn("CORS origin '*' combined with allowCredentials(true) — "
                        + "browsers will reject such CORS requests per the Fetch spec. "
                        + "Use explicit origins instead.");
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalStateException("cors.allowed-origins不能为空");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        boolean hasWildcard = normalizeAllowedOrigins().stream().anyMatch("*"::equals);
        String[] configuredOrigins = normalizeAllowedOrigins().toArray(new String[0]);
        registry.addMapping("/**")
                .allowedOrigins(configuredOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Refresh-Token")
                .allowCredentials(!hasWildcard);
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

    private boolean isLocalProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "local".equalsIgnoreCase(p));
    }
}
