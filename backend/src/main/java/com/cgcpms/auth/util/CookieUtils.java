package com.cgcpms.auth.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility for reading/writing/clearing HttpOnly authentication cookies.
 * All cookies use SameSite=Strict for CSRF protection.
 * {@code Secure} flag is enabled when {@code cookie.secure=true} (production).
 */
@Component
public class CookieUtils {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    /** Refresh token is only sent to the refresh endpoint — minimizes exposure. */
    private static final String REFRESH_PATH = "/api/auth/refresh";

    @Value("${cookie.secure:false}")
    private boolean secure;

    /**
     * Set the access-token cookie (HttpOnly, SameSite=Strict).
     * @param maxAgeSeconds TTL in seconds; cookie expires at this duration.
     */
    public void setAccessTokenCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        String value = buildCookieValue(ACCESS_TOKEN_COOKIE, token, maxAgeSeconds, "/api", true);
        response.addHeader("Set-Cookie", value);
    }

    /**
     * Set the refresh-token cookie (HttpOnly, SameSite=Strict, scoped to /api/auth/refresh).
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        String value = buildCookieValue(REFRESH_TOKEN_COOKIE, token, maxAgeSeconds, REFRESH_PATH, true);
        response.addHeader("Set-Cookie", value);
    }

    /**
     * Clear both auth cookies by setting Max-Age=0.
     */
    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildClearValue(ACCESS_TOKEN_COOKIE, "/api"));
        response.addHeader("Set-Cookie", buildClearValue(REFRESH_TOKEN_COOKIE, REFRESH_PATH));
    }

    /**
     * Read a cookie value by name, or null if absent.
     */
    public String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    // ---- private helpers ----

    private String buildCookieValue(String name, String token, long maxAgeSeconds,
                                            String path, boolean httpOnly) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(token);
        sb.append("; Path=").append(path);
        sb.append("; Max-Age=").append(maxAgeSeconds);
        if (httpOnly) sb.append("; HttpOnly");
        sb.append("; SameSite=Strict");
        if (secure) sb.append("; Secure");
        return sb.toString();
    }

    private String buildClearValue(String name, String path) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=; Path=").append(path);
        sb.append("; Max-Age=0; HttpOnly; SameSite=Strict");
        if (secure) sb.append("; Secure");
        return sb.toString();
    }
}
