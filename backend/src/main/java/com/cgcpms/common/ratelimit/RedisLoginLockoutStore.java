package com.cgcpms.common.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed login lockout store shared across instances.
 */
@Component
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisLoginLockoutStore implements LoginLockoutStore {

    private static final String FAILURE_KEY_PREFIX = "login:lockout:fail:";
    private static final String LOCK_KEY_PREFIX = "login:lockout:lock:";

    private final StringRedisTemplate redisTemplate;

    public RedisLoginLockoutStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long getRemainingLockoutMillis(String key) {
        Long ttl = redisTemplate.getExpire(lockKey(key), TimeUnit.MILLISECONDS);
        return ttl != null && ttl > 0L ? ttl : 0L;
    }

    @Override
    public void recordFailure(String key, int threshold, long failureWindowMinutes, long lockoutDurationMinutes) {
        String failureKey = failureKey(key);
        Long count = redisTemplate.opsForValue().increment(failureKey);
        if (count == null) {
            return;
        }
        if (count == 1L) {
            redisTemplate.expire(failureKey, failureWindowMinutes, TimeUnit.MINUTES);
        }
        if (count >= threshold) {
            redisTemplate.delete(failureKey);
            redisTemplate.opsForValue().set(lockKey(key), "1", lockoutDurationMinutes, TimeUnit.MINUTES);
        }
    }

    @Override
    public void clear(String key) {
        redisTemplate.delete(failureKey(key));
        redisTemplate.delete(lockKey(key));
    }

    private String failureKey(String key) {
        return FAILURE_KEY_PREFIX + key;
    }

    private String lockKey(String key) {
        return LOCK_KEY_PREFIX + key;
    }
}
