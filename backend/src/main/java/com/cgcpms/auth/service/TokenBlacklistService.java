package com.cgcpms.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed token blacklist. Only active when StringRedisTemplate is available
 * (i.e. not in the local profile where Redis is excluded).
 *
 * <p>When Redis is unavailable, operations degrade safely:
 * <ul>
 *   <li>{@link #blacklist(String, long)} logs a warning — blacklist write is lost</li>
 *   <li>{@link #isBlacklisted(String)} returns {@code false} (safe default) and logs a warning</li>
 * </ul>
 * In both cases the system remains operational but blacklist protection is absent.
 * Monitoring should alert on these log patterns:
 * <ul>
 *   <li>{@code TOKEN_BLACKLIST_WRITE_FAILED}</li>
 *   <li>{@code TOKEN_BLACKLIST_CHECK_FAILED}</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class TokenBlacklistService {

    private static final String PREFIX = "token:blacklist:";
    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Blacklist a token using its last 32 chars as key, with given TTL. */
    public void blacklist(String token, long ttlMillis) {
        if (ttlMillis <= 0) return;
        String key = PREFIX + tokenKey(token);
        try {
            redisTemplate.opsForValue().set(key, "1", Duration.ofMillis(ttlMillis));
            log.debug("Token blacklisted with TTL: {}ms", ttlMillis);
        } catch (RuntimeException e) {
            log.warn("TOKEN_BLACKLIST_WRITE_FAILED: Redis不可用，令牌黑名单写入失败, ttl={}ms",
                    ttlMillis, e);
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
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + tokenKey(token)));
        } catch (RuntimeException e) {
            log.warn("TOKEN_BLACKLIST_CHECK_FAILED: Redis不可用，黑名单检查回退为拒绝（fail-close）", e);
            return true;
        }
    }

    /**
     * 使用 token 后 32 字符作为 Redis key。
     * 碰撞概率约 1/16^32，生产环境中可忽略。
     * 长期可考虑改用 SHA-256 哈希以完全消除碰撞风险。
     */
    private String tokenKey(String token) {
        return token.length() > 32 ? token.substring(token.length() - 32) : token;
    }
}
