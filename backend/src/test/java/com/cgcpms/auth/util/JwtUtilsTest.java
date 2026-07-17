package com.cgcpms.auth.util;

import com.cgcpms.auth.config.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JWT compact claims")
class JwtUtilsTest {

    @Test
    @DisplayName("permissions are compressed without changing their values")
    void permissionsUseCompressedClaim() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        properties.setExpiration(900_000L);
        properties.setRefreshExpiration(604_800_000L);
        JwtUtils jwtUtils = new JwtUtils(properties);

        String token = jwtUtils.generateToken(1L, "admin", 0L,
                List.of("SUPER_ADMIN"),
                List.of("measurement:query", "measurement:submit", "measurement:owner:review"));
        var claims = jwtUtils.parseToken(token);

        assertNull(claims.get(JwtUtils.CLAIM_PERMISSIONS));
        assertTrue(claims.get(JwtUtils.CLAIM_PERMISSIONS_GZIP, String.class).length() > 0);
        assertEquals(List.of("measurement:query", "measurement:submit", "measurement:owner:review"),
                jwtUtils.getPermissionCodes(claims));
        assertTrue(jwtUtils.validateToken(token));
    }

    @Test
    @DisplayName("large permission sets stay safely below the browser cookie limit")
    void largePermissionSetFitsCookieLimit() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        properties.setExpiration(900_000L);
        properties.setRefreshExpiration(604_800_000L);
        JwtUtils jwtUtils = new JwtUtils(properties);
        List<String> permissions = java.util.stream.IntStream.range(0, 400)
                .mapToObj(i -> "module:" + i + ":query")
                .toList();

        String token = jwtUtils.generateToken(1L, "admin", 0L,
                List.of("SUPER_ADMIN"), permissions);

        assertTrue(token.length() < 3800, "access token must leave room for cookie attributes");
        assertEquals(permissions, jwtUtils.getPermissionCodes(jwtUtils.parseToken(token)));
    }
}
