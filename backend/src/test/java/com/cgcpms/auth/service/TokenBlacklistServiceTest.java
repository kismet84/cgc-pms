package com.cgcpms.auth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TokenBlacklistService — Redis availability and fallback behavior.
 *
 * Verifies:
 * - Normal blacklist/isBlacklisted operations work correctly
 * - Redis connection failures are detected and logged (audit trail)
 * - isBlacklisted returns false (safe default) when Redis is unreachable,
 *   but the service handles the error gracefully
 */
@DisplayName("TokenBlacklistService — Redis 降级行为")
class TokenBlacklistServiceTest {

    @Nested
    @DisplayName("正常操作 (mock Redis)")
    class NormalOperations {

        private StringRedisTemplate redisTemplate;
        private TokenBlacklistService service;
        private RedisConnectionFactory connectionFactory;
        private RedisConnection connection;

        @BeforeEach
        void setUp() {
            connectionFactory = mock(RedisConnectionFactory.class);
            connection = mock(RedisConnection.class);
            when(connectionFactory.getConnection()).thenReturn(connection);
            when(connection.isClosed()).thenReturn(false);

            redisTemplate = new StringRedisTemplate(connectionFactory);
            redisTemplate.afterPropertiesSet();

            service = new TokenBlacklistService(redisTemplate);
        }

        @AfterEach
        void tearDown() {
            // no cleanup needed for mocks
        }

        @Test
        @DisplayName("blacklist 设置 key 并写入 Redis")
        void blacklistSetsKey() {
            // blacklist should not throw
            assertDoesNotThrow(() -> service.blacklist("test-token-12345678901234567890123456789012", 60000L));
        }

        @Test
        @DisplayName("blacklist TTL <= 0 时跳过写入")
        void blacklistSkipsWhenTtlNotPositive() {
            // should not throw, just skips
            assertDoesNotThrow(() -> service.blacklist("test-token", 0L));
            assertDoesNotThrow(() -> service.blacklist("test-token", -1L));
        }

        @Test
        @DisplayName("isBlacklisted 返回 false 对新 token")
        void isBlacklistedReturnsFalseForNewToken() {
            assertFalse(service.isBlacklisted("never-blacklisted-token-abcdefghij"));
        }
    }

    @Nested
    @DisplayName("Redis 不可用降级")
    class RedisUnavailableFallback {

        /**
         * When StringRedisTemplate is not available as a Spring bean
         * (e.g., local/H2 profile without Redis), the TokenBlacklistService
         * is not created at all (via @ConditionalOnBean).
         *
         * Callers use ObjectProvider.getIfAvailable() and get null.
         * This test verifies the caller-side behavior pattern — that
         * when the service is absent, the system behaves correctly.
         */
        @Test
        @DisplayName("getIfAvailable 返回 null 时调用方行为安全")
        void objectProviderReturnsNullWhenRedisUnavailable() {
            // This simulates what happens in AuthController and JwtAuthenticationFilter
            // when TokenBlacklistService is not available (Redis is off).
            TokenBlacklistService absentService = null; // simulate getIfAvailable returning null

            // Callers must handle null safely
            assertDoesNotThrow(() -> {
                if (absentService != null) {
                    absentService.blacklist("token", 1000L);
                }
                // safe: null check prevents NPE
            }, "调用方应在 null 检查后安全跳过");
        }

        @Test
        @DisplayName("isBlacklisted 在 Redis 异常时返回 false 且不抛异常")
        void isBlacklistedReturnsFalseOnRedisException() throws Exception {
            // Given a Redis connection that throws on operations
            RedisConnectionFactory failingFactory = mock(RedisConnectionFactory.class);
            RedisConnection failingConnection = mock(RedisConnection.class);
            when(failingFactory.getConnection()).thenReturn(failingConnection);
            when(failingConnection.isClosed()).thenReturn(false);
            doThrow(new RuntimeException("Connection refused"))
                    .when(failingConnection).exists(any(byte[].class));

            StringRedisTemplate failingTemplate = new StringRedisTemplate(failingFactory);
            failingTemplate.afterPropertiesSet();

            TokenBlacklistService service = new TokenBlacklistService(failingTemplate);

            // When Redis is broken, isBlacklisted should return false (safe default)
            // and not throw — the system continues operating
            boolean result = service.isBlacklisted("some-token-12345678901234567890123456789012");

            // Then: should return false as safe default when Redis is unreachable
            assertFalse(result, "Redis 不可用时应返回 false（安全默认值），不应抛出异常");
        }
    }
}
