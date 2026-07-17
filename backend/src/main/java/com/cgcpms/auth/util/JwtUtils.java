package com.cgcpms.auth.util;

import com.cgcpms.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * JWT helper for generating and parsing tokens using the jjwt 0.12.x API.
 */
@Component
public class JwtUtils {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_TENANT_ID = "tenantId";
    public static final String CLAIM_ROLES = "roleCodes";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String GZIP_PERMISSION_PREFIX = "gz:";
    private static final int MAX_PERMISSION_PAYLOAD_BYTES = 64 * 1024;

    private final JwtProperties jwtProperties;
    private final SecretKey key;

    public JwtUtils(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username, Long tenantId,
                                List<String> roleCodes, List<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpiration());
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TENANT_ID, tenantId)
                .claim(CLAIM_ROLES, roleCodes)
                .claim(CLAIM_PERMISSIONS, encodePermissionClaim(permissions))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Encode permission authorities compactly enough for an HttpOnly cookie.
     * Small claims retain the legacy comma-separated representation; larger
     * claims use signed gzip content with an explicit prefix.
     */
    public static String encodePermissionClaim(List<String> permissions) {
        List<String> normalized = permissions == null
                ? List.of()
                : permissions.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        String legacy = String.join(",", normalized);
        if (legacy.isEmpty()) {
            return legacy;
        }

        String compressed = GZIP_PERMISSION_PREFIX + gzip(String.join("\n", normalized));
        return compressed.length() < legacy.length() ? compressed : legacy;
    }

    /** Decode current compressed claims and both historical claim formats. */
    public static List<String> decodePermissionClaim(Object value) {
        if (value instanceof List<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return Collections.emptyList();
        }
        if (text.startsWith(GZIP_PERMISSION_PREFIX)) {
            return splitPermissions(gunzip(text.substring(GZIP_PERMISSION_PREFIX.length())), "\n");
        }
        return splitPermissions(text, ",");
    }

    private static List<String> splitPermissions(String value, String delimiter) {
        return List.of(value.split(delimiter)).stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static String gzip(String value) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(value.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode permission claim", ex);
        }
    }

    private static String gunzip(String value) {
        try {
            byte[] encoded = Base64.getUrlDecoder().decode(value);
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(encoded))) {
                byte[] decoded = gzip.readNBytes(MAX_PERMISSION_PAYLOAD_BYTES + 1);
                if (decoded.length > MAX_PERMISSION_PAYLOAD_BYTES) {
                    throw new IllegalArgumentException("Permission claim exceeds decoded size limit");
                }
                return new String(decoded, StandardCharsets.UTF_8);
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid compressed permission claim", ex);
        }
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshExpiration());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            return TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns remaining TTL in millis for a valid token, or 0 if expired/invalid. */
    public long getRemainingTtlMillis(String token) {
        try {
            Date expiry = parseToken(token).getExpiration();
            long remaining = expiry.getTime() - System.currentTimeMillis();
            return Math.max(remaining, 0L);
        } catch (Exception e) {
            return 0L;
        }
    }

}
