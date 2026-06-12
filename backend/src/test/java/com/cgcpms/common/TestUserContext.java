package com.cgcpms.common;

import com.cgcpms.auth.context.UserContext;
import io.jsonwebtoken.Jwts;

import java.util.List;

/**
 * Test helper for constructing UserContext with proper roleCodes.
 * <p>
 * Real JWT flow injects roleCodes via {@code JwtUtils.CLAIM_ROLES} ("roleCodes").
 * Tests that construct claims without roleCodes cause {@link UserContext#hasRole(String)}
 * to always return {@code false}, even for ADMIN.
 * </p>
 */
public final class TestUserContext {

    private TestUserContext() {}

    /** Default tenant ID used in most test datasets. */
    public static final long TENANT_0 = 0L;

    /** Admin user ID matching test seed data. */
    public static final long USER_ADMIN = 1L;

    /**
     * Build admin claims with all required fields set.
     * Equivalent to what a real JWT would produce for the admin user.
     */
    public static void setAdmin(long tenantId, long userId) {
        UserContext.set(Jwts.claims()
                .add("userId", userId)
                .add("username", "admin")
                .add("tenantId", tenantId)
                .add("roleCodes", List.of("ADMIN"))
                .build());
    }

    /**
     * Build claims for a specific user with given roles.
     */
    public static void setUser(long tenantId, long userId, String username, List<String> roleCodes) {
        UserContext.set(Jwts.claims()
                .add("userId", userId)
                .add("username", username)
                .add("tenantId", tenantId)
                .add("roleCodes", roleCodes != null ? roleCodes : List.of())
                .build());
    }

    /** Clear the thread-local context. Call in @AfterEach. */
    public static void clear() {
        UserContext.clear();
    }
}
