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
    public static final String CLAIM_PERMISSIONS_GZIP = "pc";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

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
                // 权限码数量较多时，即使改成逗号分隔字符串，访问令牌仍可能超过
                // 浏览器单 Cookie 约 4 KiB 的上限。使用 GZIP + Base64URL 紧凑声明，
                // 解析端继续兼容历史 permissions 数组/字符串格式。
                .claim(CLAIM_PERMISSIONS_GZIP, compressPermissions(permissions))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
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

    /**
     * Read permission codes from the compact claim, while remaining compatible
     * with access tokens issued before the compact claim was introduced.
     */
    public List<String> getPermissionCodes(Claims claims) {
        Object legacy = claims.get(CLAIM_PERMISSIONS);
        if (legacy instanceof String text) {
            return splitPermissions(text);
        }
        if (legacy instanceof List<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }

        Object compact = claims.get(CLAIM_PERMISSIONS_GZIP);
        if (!(compact instanceof String encoded) || encoded.isBlank()) {
            return Collections.emptyList();
        }
        try {
            byte[] compressed = Base64.getUrlDecoder().decode(encoded);
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                byte[] bytes = gzip.readAllBytes();
                return splitPermissions(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (IllegalArgumentException | IOException ex) {
            // Fail closed: a malformed permission claim grants no authorities.
            return Collections.emptyList();
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

    private String compressPermissions(List<String> permissions) {
        String joined = String.join(",", permissions == null ? List.of() : permissions);
        if (joined.isEmpty()) {
            return "";
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
                gzip.write(joined.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("无法压缩 JWT 权限声明", ex);
        }
    }

    private List<String> splitPermissions(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
