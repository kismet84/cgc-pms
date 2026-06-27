package com.cgcpms.common.aspect;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.annotation.RateLimit;
import com.cgcpms.common.annotation.RateLimitKey;
import com.cgcpms.common.exception.RateLimitExceededException;
import com.cgcpms.common.ratelimit.RateLimitCounterStore;
import com.cgcpms.common.result.ApiResponse;
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
 * Enforces {@link RateLimit} constraints on annotated controller methods,
 * plus account-lockout protection for login endpoints.
 *
 * <h3>Rate limiting</h3>
 * Each dimension key (IP / USER / TENANT / IP_AND_ACCOUNT) gets a counter
 * that resets after the configured window. The actual counter storage is
 * delegated to {@link RateLimitCounterStore} (Redis or in-memory fallback).
 *
 * <h3>Account lockout (R-27)</h3>
 * After 5 failed login attempts from the same IP within a 15-minute window,
 * the IP is locked out for 30 minutes. During the lockout period any login
 * request from that IP is rejected before reaching the auth logic.
 *
 * <ul>
 *   <li><b>Failure window</b>: 15 minutes (the span during which failed attempts
 *       are counted towards the lockout threshold).</li>
 *   <li><b>Lockout duration</b>: 30 minutes (how long the IP stays locked after
 *       the threshold is reached).</li>
 *   <li><b>Threshold</b>: 5 failed attempts.</li>
 * </ul>
 *
 * <p><b>Reset behaviour</b>: A <em>successful</em> login from the same IP clears
 * the failure counter for that IP. This prevents legitimate users from being
 * locked out because of a few typos interspersed with a correct credential entry.
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    /** Max time an idle entry stays in cache before eviction (prevents memory leaks). */
    private static final int MAX_IDLE_SECONDS = 300;
    private static final int MAX_CACHE_SIZE = 10_000;

    // ---- Account-lockout constants (R-27) ----
    /** Number of failed attempts before lockout triggers. */
    private static final int LOCKOUT_THRESHOLD = 5;
    /** Window (minutes) in which failed attempts are counted. */
    private static final long FAILURE_WINDOW_MINUTES = 15;
    /** Lockout duration in minutes. */
    private static final long LOCKOUT_DURATION_MINUTES = 30;

    // Lockout entry array indices
    private static final int LX_LOCKOUT_UNTIL = 0;
    private static final int LX_FIRST_FAILURE = 1;
    private static final int LX_FAILURE_COUNT = 2;

    /**
     * Pluggable counter store — injected via Spring (Redis or fallback).
     */
    private final RateLimitCounterStore counterStore;

    /**
     * IP → [lockoutUntilMs, firstFailureMs, failureCount] — R-27 account lockout.
     * <ul>
     *   <li>{@code lockoutUntilMs}: epoch-millis until lockout ends, or 0 when not locked.</li>
     *   <li>{@code firstFailureMs}: epoch-millis of the first failure in the current window.</li>
     *   <li>{@code failureCount}: number of failures in the current window.</li>
     * </ul>
     * Entries expire 5 minutes after the last access to prevent memory leaks.
     */
    private final Cache<String, long[]> lockoutCache = CacheBuilder.newBuilder()
            .expireAfterAccess(MAX_IDLE_SECONDS, TimeUnit.SECONDS)
            .maximumSize(MAX_CACHE_SIZE)
            .build();

    public RateLimitAspect(RateLimitCounterStore counterStore) {
        this.counterStore = counterStore;
    }

    /* =========================================================================
     * Rate limit advice
     * ========================================================================= */

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String ip = resolveClientIp();

        // --- R-27: check account lockout BEFORE proceeding ---
        checkLockout(ip);

        String endpoint = joinPoint.getSignature().toShortString();
        RateLimitKey keyDimension = rateLimit.key();
        String limitKey = buildLimitKey(endpoint, keyDimension, ip);

        long count = counterStore.increment(limitKey, rateLimit.windowSeconds());

        if (count > rateLimit.maxRequests()) {
            log.warn("Rate limit exceeded: keyDigest={}, endpoint={}, count={}, limit={}",
                    safeKeyDigest(limitKey), endpoint, count, rateLimit.maxRequests());
            throw new RateLimitExceededException(
                    String.format("请求过于频繁，请在 %d 秒后重试", rateLimit.windowSeconds()));
        }

        // Proceed with the actual method
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            // --- R-27: any exception thrown during login counts as a failed attempt ---
            recordFailedAttempt(ip);
            throw t;
        }

        // --- R-27: check ApiResponse code for login failure ---
        if (result instanceof ApiResponse<?> apiResp
                && !ApiResponse.SUCCESS_CODE.equals(apiResp.getCode())) {
            recordFailedAttempt(ip);
        } else {
            // Successful login (or non-ApiResponse return) – clear failure counter
            clearFailureCounter(ip);
        }

        return result;
    }

    /* =========================================================================
     * Multi-dimensional key builder
     * ========================================================================= */

    /**
     * Build a rate-limit key in the format {@code endpoint:dimension:value}.
     */
    private String buildLimitKey(String endpoint, RateLimitKey dimension, String ip) {
        return switch (dimension) {
            case IP -> endpoint + ":ip:" + ip;
            case USER -> {
                Long userId = UserContext.getCurrentUserId();
                yield endpoint + ":user:" + (userId != null ? userId : ip);
            }
            case TENANT -> {
                Long tenantId = UserContext.getCurrentTenantId();
                yield endpoint + ":tenant:" + (tenantId != null ? tenantId : ip);
            }
            case IP_AND_ACCOUNT -> {
                Long userId = UserContext.getCurrentUserId();
                yield endpoint + ":ip_account:" + ip + ":" + (userId != null ? userId : "anon");
            }
        };
    }

    /* =========================================================================
     * R-27 Account-lockout helpers
     * ========================================================================= */

    /**
     * Reject the request if the IP is currently in the lockout period.
     *
     * @throws RateLimitExceededException with a lockout-specific message
     */
    private void checkLockout(String ip) {
        long[] lockEntry = lockoutCache.getIfPresent(ip);
        if (lockEntry == null) {
            return;
        }
        long lockoutUntil = lockEntry[LX_LOCKOUT_UNTIL];
        if (lockoutUntil == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < lockoutUntil) {
            long remainingMinutes = (lockoutUntil - now) / 60_000 + 1;
            log.warn("Account locked out: ip={}, remaining={}min", ip, remainingMinutes);
            throw new RateLimitExceededException(
                    String.format("登录失败次数过多，账号已锁定，请在 %d 分钟后重试", remainingMinutes));
        }
        // Lockout has expired – clear the entry
        lockoutCache.invalidate(ip);
    }

    /**
     * Record a failed login attempt for the given IP.
     * <p>
     * Failures within {@link #FAILURE_WINDOW_MINUTES} minutes are accumulated.
     * Once the count reaches {@link #LOCKOUT_THRESHOLD}, the IP is locked out for
     * {@link #LOCKOUT_DURATION_MINUTES} minutes and the failure counter is reset.
     */
    private void recordFailedAttempt(String ip) {
        long now = System.currentTimeMillis();
        lockoutCache.asMap().compute(ip, (key, old) -> {
            if (old == null || (now - old[LX_FIRST_FAILURE]) > TimeUnit.MINUTES.toMillis(FAILURE_WINDOW_MINUTES)) {
                // Fresh failure window
                return new long[]{0L, now, 1L};
            }
            old[LX_FAILURE_COUNT]++;
            if (old[LX_FAILURE_COUNT] >= LOCKOUT_THRESHOLD) {
                long lockoutUntil = now + TimeUnit.MINUTES.toMillis(LOCKOUT_DURATION_MINUTES);
                // Lock the IP and clear the failure counter for the locked entry
                return new long[]{lockoutUntil, 0L, 0L};
            }
            return old;
        });
    }

    /**
     * Clear the failure counter for the given IP.
     * Called when a login succeeds, so legitimate users who occasionally mistype
     * are not penalised.
     */
    private void clearFailureCounter(String ip) {
        lockoutCache.invalidate(ip);
    }

    /* =========================================================================
     * IP resolution (unchanged)
     * ========================================================================= */

    private String resolveClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "unknown";
        }
        // Prefer Nginx X-Real-IP (overridden real client IP)
        String ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        // Fallback: first IP from X-Forwarded-For
        ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
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

    private String safeKeyDigest(String limitKey) {
        if (limitKey == null) {
            return "null";
        }
        int length = limitKey.length();
        if (length <= 16) {
            return "len=" + length;
        }
        return "len=" + length + ":" + Integer.toHexString(limitKey.hashCode());
    }
}
