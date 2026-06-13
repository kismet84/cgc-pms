package com.cgcpms.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed token blacklist. Only active when StringRedisTemplate is available
 * (i.e. not in the local profile where Redis is excluded).
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
        redisTemplate.opsForValue().set(key, "1", Duration.ofMillis(ttlMillis));
        log.debug("Token blacklisted with TTL: {}ms", ttlMillis);
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + tokenKey(token)));
    }

    private String tokenKey(String token) {
        return token.length() > 32 ? token.substring(token.length() - 32) : token;
    }
}
