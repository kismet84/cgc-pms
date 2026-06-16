package com.cgcpms.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rate-limit annotation for brute-force protection on sensitive endpoints.
 * Uses Guava in-memory cache keyed by client IP address.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Maximum number of requests allowed within the window. */
    int maxRequests() default 10;

    /** Time window in seconds. */
    int windowSeconds() default 60;
}
