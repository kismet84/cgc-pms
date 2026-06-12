package com.cgcpms.auth.context;

import com.cgcpms.auth.util.JwtUtils;
import io.jsonwebtoken.Claims;

import java.util.Collections;
import java.util.List;

/**
 * Holds the authenticated principal for the current request thread.
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> ROLES = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * Populate the context from verified JWT claims.
     */
    @SuppressWarnings("unchecked")
    public static void set(Claims claims) {
        USER_ID.set(claims.get(JwtUtils.CLAIM_USER_ID, Long.class));
        USERNAME.set(claims.get(JwtUtils.CLAIM_USERNAME, String.class));
        TENANT_ID.set(claims.get(JwtUtils.CLAIM_TENANT_ID, Long.class));
        List<String> roles = claims.get(JwtUtils.CLAIM_ROLES, List.class);
        ROLES.set(roles != null ? roles : Collections.emptyList());
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        TENANT_ID.remove();
        ROLES.remove();
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

    public static List<String> getCurrentRoles() {
        List<String> roles = ROLES.get();
        return roles != null ? roles : Collections.emptyList();
    }

    public static boolean hasRole(String roleCode) {
        return getCurrentRoles().contains(roleCode);
    }

    /**
     * Check if current user has any of the specified roles.
     */
    public static boolean hasAnyRole(String... roleCodes) {
        List<String> currentRoles = getCurrentRoles();
        if (currentRoles == null || currentRoles.isEmpty()) {
            return false;
        }
        return java.util.Arrays.stream(roleCodes).anyMatch(currentRoles::contains);
    }
}
