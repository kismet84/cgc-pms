package com.cgcpms.auth.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration. Allowed origins are driven by the
 * {@code cors.allowed-origins} property so each profile can declare
 * its own set without code changes.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @PostConstruct
    void validateOrigins() {
        for (String origin : allowedOrigins) {
            if ("*".equals(origin.trim())) {
                log.warn("CORS origin '*' combined with allowCredentials(true) — "
                        + "browsers will reject such CORS requests per the Fetch spec. "
                        + "Use explicit origins instead.");
            }
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Refresh-Token")
                .allowCredentials(true);
    }
}
