package com.cgcpms.auth.context;

import com.cgcpms.auth.util.JwtUtils;
import io.jsonwebtoken.Claims;

/**
 * Holds the authenticated principal for the current request thread.
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * Populate the context from verified JWT claims.
     */
    public static void set(Claims claims) {
        USER_ID.set(claims.get(JwtUtils.CLAIM_USER_ID, Long.class));
        USERNAME.set(claims.get(JwtUtils.CLAIM_USERNAME, String.class));
        TENANT_ID.set(claims.get(JwtUtils.CLAIM_TENANT_ID, Long.class));
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        TENANT_ID.remove();
    }

    public static Long getCurrentUserId() {
        return USER_ID.get();
    }

    public static String getCurrentUsername() {
        return USERNAME.get();
    }

    public static Long getCurrentTenantId() {
        return TENANT_ID.get();
    }
}
