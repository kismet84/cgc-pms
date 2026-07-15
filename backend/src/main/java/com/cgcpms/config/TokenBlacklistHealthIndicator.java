package com.cgcpms.config;

import com.cgcpms.auth.service.TokenBlacklistService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Health signal for the Redis-backed token blacklist.
 * Production treats a missing blacklist service as DOWN; local profiles keep
 * the existing fallback behavior but expose an UNKNOWN component signal.
 */
@Component
public class TokenBlacklistHealthIndicator implements HealthIndicator {

    private final ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider;
    private final Environment environment;

    public TokenBlacklistHealthIndicator(ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider,
                                         Environment environment) {
        this.tokenBlacklistServiceProvider = tokenBlacklistServiceProvider;
        this.environment = environment;
    }

    @Override
    public Health health() {
        TokenBlacklistService service = tokenBlacklistServiceProvider.getIfAvailable();
        boolean prod = isProdProfile();
        if (service == null) {
            Health.Builder builder = prod ? Health.down() : Health.unknown();
            return builder
                    .withDetail("category", "BLACKLIST_UNAVAILABLE")
                    .withDetail("reason", "Token blacklist service bean not available")
                    .withDetail("failClosed", prod)
                    .build();
        }
        if (!service.isEnabled()) {
            return Health.unknown()
                    .withDetail("category", "TOKEN_BLACKLIST_DISABLED")
                    .withDetail("failClosed", false)
                    .build();
        }
        if (service.isAvailable()) {
            return Health.up()
                    .withDetail("category", "TOKEN_BLACKLIST_AVAILABLE")
                    .build();
        }
        return Health.down()
                .withDetail("category", "TOKEN_BLACKLIST_CHECK_FAILED")
                .withDetail("reason", "Redis token blacklist health check failed")
                .build();
    }

    private boolean isProdProfile() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }
}
