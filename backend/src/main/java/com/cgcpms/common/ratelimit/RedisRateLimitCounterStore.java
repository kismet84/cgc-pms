package com.cgcpms.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed rate-limit counter store.
 * Uses {@code INCR} with TTL set on first write for atomic window enforcement.
 */
@Component
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisRateLimitCounterStore implements RateLimitCounterStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitCounterStore.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitCounterStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long increment(String key, int windowSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            log.error("Redis INCR returned null for key={}, falling back to reject", key);
            return Long.MAX_VALUE;
        }
        // Set TTL only when the key was freshly created (count == 1)
        if (count == 1) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return count;
    }
}
