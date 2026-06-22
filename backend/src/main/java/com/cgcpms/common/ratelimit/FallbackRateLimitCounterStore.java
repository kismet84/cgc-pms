package com.cgcpms.common.ratelimit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Fallback in-memory counter store when Redis is unavailable.
 * Uses a bounded Guava cache with per-window timestamp + AtomicLong counters.
 * Logs a warning on every increment so operators can detect the degraded state.
 */
@ConditionalOnMissingBean(RedisRateLimitCounterStore.class)
@Component
public class FallbackRateLimitCounterStore implements RateLimitCounterStore {

    private static final Logger log = LoggerFactory.getLogger(FallbackRateLimitCounterStore.class);

    private static final int MAX_ENTRIES = 50_000;
    private static final int MAX_IDLE_MINUTES = 30;

    /**
     * Window entry: [windowStartMs, AtomicLong counter].
     * When the current time exceeds windowStartMs + windowSeconds * 1000,
     * the entry is replaced.
     */
    private final Cache<String, long[]> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(MAX_IDLE_MINUTES, TimeUnit.MINUTES)
            .maximumSize(MAX_ENTRIES)
            .build();

    private static final int IDX_WINDOW_START = 0;
    private static final int IDX_COUNT = 1;

    @Override
    public long increment(String key, int windowSeconds) {
        log.warn("Rate limiting using in-memory fallback store (Redis unavailable)");
        long[] result = cache.asMap().compute(key, (k, old) -> {
            long now = System.currentTimeMillis();
            long windowMs = windowSeconds * 1000L;
            if (old == null || (now - old[IDX_WINDOW_START]) > windowMs) {
                return new long[]{now, 1L};
            }
            old[IDX_COUNT]++;
            return old;
        });
        return result != null ? result[IDX_COUNT] : 1L;
    }

    /**
     * Clear all counters (for testing).
     */
    public void clear() {
        cache.invalidateAll();
    }
}
