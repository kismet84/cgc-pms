package com.cgcpms.common.ratelimit;

/**
 * Shared store for login lockout state.
 */
public interface LoginLockoutStore {

    /**
     * @return remaining lockout time in milliseconds, or 0 when not locked
     */
    long getRemainingLockoutMillis(String key);

    /**
     * Record one failed login attempt and lock the key once threshold is reached.
     */
    void recordFailure(String key, int threshold, long failureWindowMinutes, long lockoutDurationMinutes);

    /**
     * Clear any failure or lockout state for the key.
     */
    void clear(String key);
}
