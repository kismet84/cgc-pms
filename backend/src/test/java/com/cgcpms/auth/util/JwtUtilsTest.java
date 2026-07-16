package com.cgcpms.auth.util;

import com.cgcpms.auth.config.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JWT compact claims")
class JwtUtilsTest {

    @Test
    @DisplayName("permissions are encoded as a compact string without changing their values")
    void permissionsUseCompactStringClaim() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        properties.setExpiration(900_000L);
        properties.setRefreshExpiration(604_800_000L);
        JwtUtils jwtUtils = new JwtUtils(properties);

        String token = jwtUtils.generateToken(1L, "admin", 0L,
                List.of("SUPER_ADMIN"),
                List.of("measurement:query", "measurement:submit", "measurement:owner:review"));
        Object claim = jwtUtils.parseToken(token).get(JwtUtils.CLAIM_PERMISSIONS);

        assertInstanceOf(String.class, claim);
        assertEquals("measurement:query,measurement:submit,measurement:owner:review", claim);
        assertTrue(jwtUtils.validateToken(token));
    }

    @Test
    @DisplayName("large permission sets are compressed and stay inside the browser cookie budget")
    void largePermissionSetStaysInsideCookieBudget() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        properties.setExpiration(900_000L);
        properties.setRefreshExpiration(604_800_000L);
        JwtUtils jwtUtils = new JwtUtils(properties);
        List<String> permissions = IntStream.range(0, 600)
                .mapToObj(index -> "project:domain" + (index % 40) + ":operation" + index)
                .toList();

        String token = jwtUtils.generateToken(1L, "demo_dev_super_admin", 0L,
                List.of("SUPER_ADMIN"), permissions);
        Object claim = jwtUtils.parseToken(token).get(JwtUtils.CLAIM_PERMISSIONS);

        assertTrue(claim instanceof String text && text.startsWith("gz:"));
        assertEquals(permissions, JwtUtils.decodePermissionClaim(claim));
        assertTrue(token.length() < 3_800, "access token must leave room for cookie attributes");
    }
}
