package com.cgcpms.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed token blacklist. The service is always registered so an enabled
 * deployment cannot silently lose blacklist protection because Redis auto-configuration
 * was evaluated later. Local/test profiles must disable it explicitly.
 *
 * <p>When Redis is unavailable, operations degrade safely:
 * <ul>
 *   <li>{@link #blacklist(String, long)} logs a warning and returns {@code false}</li>
 *   <li>{@link #isBlacklisted(String)} returns {@code true} (fail-close) and logs a warning</li>
 * </ul>
 * Monitoring should alert on these log patterns:
 * <ul>
 *   <li>{@code TOKEN_BLACKLIST_WRITE_FAILED}</li>
 *   <li>{@code TOKEN_BLACKLIST_CHECK_FAILED}</li>
 * </ul>
 */
@Slf4j
@Service
public class TokenBlacklistService {

    private static final String PREFIX = "token:blacklist:";
    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this(redisTemplate, true);
    }

    @Autowired
    public TokenBlacklistService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                 Environment environment) {
        this(redisTemplateProvider.getIfAvailable(), environment.getProperty(
                "auth.token-blacklist.enabled",
                Boolean.class,
                !environment.acceptsProfiles(Profiles.of("local", "test"))));
    }

    public TokenBlacklistService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                 boolean enabled) {
        this(redisTemplateProvider.getIfAvailable(), enabled);
    }

    private TokenBlacklistService(StringRedisTemplate redisTemplate, boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
    }

    /** Blacklist a token using its last 32 chars as key, with given TTL. */
    public boolean blacklist(String token, long ttlMillis) {
        if (!enabled) return true;
        if (ttlMillis <= 0) return true;
        if (redisTemplate == null) {
            log.warn("TOKEN_BLACKLIST_WRITE_FAILED: Redis模板不可用，令牌黑名单写入失败");
            return false;
        }
        String key = PREFIX + tokenKey(token);
        try {
            redisTemplate.opsForValue().set(key, "1", Duration.ofMillis(ttlMillis));
            log.debug("Token blacklisted with TTL: {}ms", ttlMillis);
            return true;
        } catch (RuntimeException e) {
            log.warn("TOKEN_BLACKLIST_WRITE_FAILED: Redis不可用，令牌黑名单写入失败, ttl={}ms, errorType={}",
                    ttlMillis, safeErrorType(e));
            return false;
        }
    }

    /**
     * Check if a token is blacklisted.
     *
     * @return {@code true} if the token is known to be blacklisted OR if Redis is unreachable
     *         (fail-close — in the failure case the system refuses the token to prevent
     *         revoked tokens from being used);
     *         {@code false} only if Redis confirms the key does not exist.
     */
    public boolean isBlacklisted(String token) {
        if (!enabled) return false;
        if (redisTemplate == null) {
            log.warn("TOKEN_BLACKLIST_CHECK_FAILED: Redis模板不可用，黑名单检查回退为拒绝（fail-close）");
            return true;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + tokenKey(token)));
        } catch (RuntimeException e) {
            log.warn("TOKEN_BLACKLIST_CHECK_FAILED: Redis不可用，黑名单检查回退为拒绝（fail-close）, errorType={}",
                    safeErrorType(e));
            return true;
        }
    }

    /**
     * Lightweight probe for actuator health. It intentionally avoids logging
     * Redis exception messages because they may contain connection details.
     */
    public boolean isAvailable() {
        if (!enabled || redisTemplate == null) return false;
        try {
            redisTemplate.hasKey(PREFIX + "__health_probe__");
            return true;
        } catch (RuntimeException e) {
            log.warn("TOKEN_BLACKLIST_CHECK_FAILED: Redis不可用，黑名单健康检查失败, errorType={}",
                    safeErrorType(e));
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 使用 token 后 32 字符作为 Redis key。
     * 碰撞概率约 1/16^32，生产环境中可忽略。
     * 长期可考虑改用 SHA-256 哈希以完全消除碰撞风险。
     */
    private String tokenKey(String token) {
        return token.length() > 32 ? token.substring(token.length() - 32) : token;
    }

    private String safeErrorType(RuntimeException e) {
        return e == null ? "RuntimeException" : e.getClass().getSimpleName();
    }
}
