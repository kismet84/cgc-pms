package com.cgcpms.common.ratelimit;

/**
 * Pluggable counter store for multi-dimensional rate limiting.
 */
public interface RateLimitCounterStore {

    /**
     * Increment the counter for the given key and return the new value.
     * The implementation is responsible for enforcing the time window.
     *
     * @param key           rate-limit key (e.g. {@code endpoint:ip:192.168.1.1})
     * @param windowSeconds time window in seconds
     * @return current count after increment
     */
    long increment(String key, int windowSeconds);
}
