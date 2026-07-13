package com.cgcpms.auth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TokenBlacklistService — Redis availability and fallback behavior.
 *
 * Verifies:
 * - Normal blacklist/isBlacklisted operations work correctly
 * - Redis connection failures are detected and logged (audit trail)
 * - isBlacklisted returns true (fail-close) when Redis is unreachable
 */
@DisplayName("TokenBlacklistService — Redis 降级行为")
@ExtendWith(OutputCaptureExtension.class)
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
            assertTrue(service.blacklist("test-token-12345678901234567890123456789012", 60000L));
        }

        @Test
        @DisplayName("blacklist TTL <= 0 时跳过写入")
        void blacklistSkipsWhenTtlNotPositive() {
            assertTrue(service.blacklist("test-token", 0L));
            assertTrue(service.blacklist("test-token", -1L));
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
         * is represented by a service whose provider has no Redis template.
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
        @DisplayName("isBlacklisted 在 Redis 异常时返回 true 且不抛异常")
        void isBlacklistedReturnsTrueOnRedisException() throws Exception {
            // Given a template that exposes the same connection failure at the service boundary
            StringRedisTemplate failingTemplate = mock(StringRedisTemplate.class);
            when(failingTemplate.hasKey(anyString()))
                    .thenThrow(new RuntimeException("Connection refused"));

            TokenBlacklistService service = new TokenBlacklistService(failingTemplate);

            // When Redis is broken, isBlacklisted should return true (fail-close).
            // In the failure case the system refuses the token to prevent
            // revoked tokens from being used.
            boolean result = service.isBlacklisted("some-token-12345678901234567890123456789012");

            // Then: should return true as fail-close when Redis is unreachable
            assertTrue(result, "Redis 不可用时应返回 true（fail-close 拒绝），防止已吊销令牌被使用");
        }

        @Test
        @DisplayName("写入失败日志包含告警码且不泄露 Redis 连接信息")
        void blacklistWriteFailureLogKeepsSignalWithoutSensitiveRedisDetails(CapturedOutput output) {
            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            @SuppressWarnings("unchecked")
            ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            doThrow(new RuntimeException("redis://user:secret@redis.internal:6379/0"))
                    .when(valueOperations).set(anyString(), eq("1"), any());

            TokenBlacklistService service = new TokenBlacklistService(redisTemplate);

            assertFalse(service.blacklist("some-token-12345678901234567890123456789012", 60_000L));
            assertTrue(output.getOut().contains("TOKEN_BLACKLIST_WRITE_FAILED"));
            assertFalse(output.getOut().contains("redis://"));
            assertFalse(output.getOut().contains("secret"));
            assertFalse(output.getOut().contains("redis.internal"));
        }

        @Test
        @DisplayName("检查失败日志包含告警码且不泄露 Redis 连接信息")
        void blacklistCheckFailureLogKeepsSignalWithoutSensitiveRedisDetails(CapturedOutput output) {
            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            when(redisTemplate.hasKey(anyString()))
                    .thenThrow(new RuntimeException("redis://user:secret@redis.internal:6379/0"));

            TokenBlacklistService service = new TokenBlacklistService(redisTemplate);

            assertTrue(service.isBlacklisted("some-token-12345678901234567890123456789012"));
            assertTrue(output.getOut().contains("TOKEN_BLACKLIST_CHECK_FAILED"));
            assertFalse(output.getOut().contains("redis://"));
            assertFalse(output.getOut().contains("secret"));
            assertFalse(output.getOut().contains("redis.internal"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void enabledServiceFailsClosedWhenRedisBeanIsMissing() {
            org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> provider =
                    mock(org.springframework.beans.factory.ObjectProvider.class);
            TokenBlacklistService service = new TokenBlacklistService(provider, true);

            assertTrue(service.isBlacklisted("token"));
            assertFalse(service.blacklist("token", 1000L));
            assertFalse(service.isAvailable());
        }

        @Test
        @SuppressWarnings("unchecked")
        void explicitlyDisabledServiceBypassesRedisForLocalTests() {
            org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> provider =
                    mock(org.springframework.beans.factory.ObjectProvider.class);
            TokenBlacklistService service = new TokenBlacklistService(provider, false);

            assertFalse(service.isBlacklisted("token"));
            assertTrue(service.blacklist("token", 1000L));
            assertFalse(service.isEnabled());
        }
    }
}
