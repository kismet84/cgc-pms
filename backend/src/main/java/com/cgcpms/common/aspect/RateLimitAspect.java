package com.cgcpms.common.aspect;

import com.cgcpms.common.annotation.RateLimit;
import com.cgcpms.common.exception.RateLimitExceededException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * Enforces {@link RateLimit} constraints on annotated controller methods.
 * <p>
 * Each client IP gets a sliding counter that resets after the configured window.
 * Stale entries are evicted by Guava after 5 minutes of inactivity (max 10 000 entries).
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    /** Max time an idle entry stays in cache before eviction (prevents memory leaks). */
    private static final int MAX_IDLE_SECONDS = 300;
    private static final int MAX_CACHE_SIZE = 10_000;

    private static final int IDX_WINDOW_START = 0;
    private static final int IDX_COUNT = 1;

    /**
     * IP → [windowStartMs, requestCount].
     * Guava handles automatic eviction; the aspect checks the time window manually
     * so different annotated methods can have different {@code windowSeconds}.
     */
    private final Cache<String, long[]> counterCache = CacheBuilder.newBuilder()
            .expireAfterWrite(MAX_IDLE_SECONDS, TimeUnit.SECONDS)
            .maximumSize(MAX_CACHE_SIZE)
            .build();

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String ip = resolveClientIp();
        long windowMs = rateLimit.windowSeconds() * 1000L;

        long[] entry = counterCache.asMap().compute(ip, (key, old) -> {
            long now = System.currentTimeMillis();
            if (old == null || (now - old[IDX_WINDOW_START]) > windowMs) {
                return new long[]{now, 1L};
            }
            old[IDX_COUNT]++;
            return old;
        });

        if (entry[IDX_COUNT] > rateLimit.maxRequests()) {
            long remainingMs = (entry[IDX_WINDOW_START] + windowMs) - System.currentTimeMillis();
            long remainingSec = Math.max(1, remainingMs / 1000);
            log.warn("Rate limit exceeded: ip={}, endpoint={}, count={}, limit={}, retryAfter={}s",
                    ip, joinPoint.getSignature().toShortString(),
                    entry[IDX_COUNT], rateLimit.maxRequests(), remainingSec);
            throw new RateLimitExceededException(
                    String.format("请求过于频繁，请在 %d 秒后重试", remainingSec));
        }

        return joinPoint.proceed();
    }

    private String resolveClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
