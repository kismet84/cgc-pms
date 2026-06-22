package com.cgcpms.common.annotation;

/**
 * Rate-limit key dimension for {@link RateLimit}.
 */
public enum RateLimitKey {
    /** Default: rate-limit by client IP address. */
    IP,
    /** Rate-limit by authenticated user ID. */
    USER,
    /** Rate-limit by tenant ID. */
    TENANT,
    /** Rate-limit by IP + account combination (more granular). */
    IP_AND_ACCOUNT
}
