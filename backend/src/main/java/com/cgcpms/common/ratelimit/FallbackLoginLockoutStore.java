package com.cgcpms.common.ratelimit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * In-memory fallback lockout store for local development or Redis outages.
 */
@Component
@ConditionalOnMissingBean(RedisLoginLockoutStore.class)
public class FallbackLoginLockoutStore implements LoginLockoutStore {

    private static final Logger log = LoggerFactory.getLogger(FallbackLoginLockoutStore.class);

    private static final int MAX_ENTRIES = 10_000;
    private static final int MAX_IDLE_MINUTES = 30;

    private static final int IDX_LOCKOUT_UNTIL = 0;
    private static final int IDX_FIRST_FAILURE = 1;
    private static final int IDX_FAILURE_COUNT = 2;

    private final Cache<String, long[]> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(MAX_IDLE_MINUTES, TimeUnit.MINUTES)
            .maximumSize(MAX_ENTRIES)
            .build();

    @Override
    public long getRemainingLockoutMillis(String key) {
        long[] entry = cache.getIfPresent(key);
        if (entry == null) {
            return 0L;
        }
        long lockoutUntil = entry[IDX_LOCKOUT_UNTIL];
        if (lockoutUntil <= 0L) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        long remaining = lockoutUntil - now;
        if (remaining <= 0L) {
            cache.invalidate(key);
            return 0L;
        }
        return remaining;
    }

    @Override
    public void recordFailure(String key, int threshold, long failureWindowMinutes, long lockoutDurationMinutes) {
        log.warn("Login lockout using in-memory fallback store (Redis unavailable)");
        long now = System.currentTimeMillis();
        long failureWindowMs = TimeUnit.MINUTES.toMillis(failureWindowMinutes);
        long lockoutMs = TimeUnit.MINUTES.toMillis(lockoutDurationMinutes);
        cache.asMap().compute(key, (ignored, old) -> {
            if (old == null || old[IDX_LOCKOUT_UNTIL] > 0L || (now - old[IDX_FIRST_FAILURE]) > failureWindowMs) {
                return threshold <= 1
                        ? new long[]{now + lockoutMs, 0L, 0L}
                        : new long[]{0L, now, 1L};
            }
            old[IDX_FAILURE_COUNT]++;
            if (old[IDX_FAILURE_COUNT] >= threshold) {
                return new long[]{now + lockoutMs, 0L, 0L};
            }
            return old;
        });
    }

    @Override
    public void clear(String key) {
        cache.invalidate(key);
    }
}
