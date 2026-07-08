package com.cgcpms.config;

import com.cgcpms.auth.service.TokenBlacklistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TokenBlacklistHealthIndicator - Redis blacklist health")
class TokenBlacklistHealthIndicatorTest {

    @Test
    @DisplayName("prod profile missing blacklist service -> DOWN")
    void shouldReportDownWhenBlacklistServiceMissingInProd() {
        TokenBlacklistHealthIndicator indicator =
                new TokenBlacklistHealthIndicator(provider(null), env("prod"));

        Health health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("BLACKLIST_UNAVAILABLE", health.getDetails().get("category"));
        assertEquals(true, health.getDetails().get("failClosed"));
        assertFalse(health.getDetails().toString().contains("REDIS_PASSWORD"));
        assertFalse(health.getDetails().toString().contains("redis://"));
    }

    @Test
    @DisplayName("local profile missing blacklist service -> UNKNOWN but not UP")
    void shouldReportUnknownWhenBlacklistServiceMissingOutsideProd() {
        TokenBlacklistHealthIndicator indicator =
                new TokenBlacklistHealthIndicator(provider(null), env("local"));

        Health health = indicator.health();

        assertEquals("UNKNOWN", health.getStatus().getCode());
        assertEquals("BLACKLIST_UNAVAILABLE", health.getDetails().get("category"));
        assertEquals(false, health.getDetails().get("failClosed"));
    }

    @Test
    @DisplayName("available blacklist service -> UP")
    void shouldReportUpWhenBlacklistServiceCanCheckRedis() {
        TokenBlacklistService service = mock(TokenBlacklistService.class);
        when(service.isAvailable()).thenReturn(true);
        TokenBlacklistHealthIndicator indicator =
                new TokenBlacklistHealthIndicator(provider(service), env("prod"));

        Health health = indicator.health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals("TOKEN_BLACKLIST_AVAILABLE", health.getDetails().get("category"));
    }

    @Test
    @DisplayName("blacklist service Redis check fails -> DOWN")
    void shouldReportDownWhenBlacklistRedisCheckFails() {
        TokenBlacklistService service = mock(TokenBlacklistService.class);
        when(service.isAvailable()).thenReturn(false);
        TokenBlacklistHealthIndicator indicator =
                new TokenBlacklistHealthIndicator(provider(service), env("prod"));

        Health health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("TOKEN_BLACKLIST_CHECK_FAILED", health.getDetails().get("category"));
        assertEquals("Redis token blacklist health check failed", health.getDetails().get("reason"));
    }

    private static MockEnvironment env(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }

    private static ObjectProvider<TokenBlacklistService> provider(TokenBlacklistService service) {
        return new ObjectProvider<>() {
            @Override
            public TokenBlacklistService getObject(Object... args) {
                if (service == null) {
                    throw new IllegalStateException("TokenBlacklistService bean not available");
                }
                return service;
            }

            @Override
            public TokenBlacklistService getIfAvailable() {
                return service;
            }

            @Override
            public TokenBlacklistService getIfUnique() {
                return service;
            }

            @Override
            public TokenBlacklistService getObject() {
                return getObject(new Object[0]);
            }
        };
    }
}
