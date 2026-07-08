package com.cgcpms.auth.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.auth.config.JwtProperties;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.dto.LoginRequest;
import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.dto.UserInfo;
import com.cgcpms.auth.service.AuthService;
import com.cgcpms.auth.service.TokenBlacklistService;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.annotation.RateLimit;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.ApiResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final CookieUtils cookieUtils;
    private final ObjectProvider<TokenBlacklistService> blacklistProvider;
    private final Environment environment;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public AuthController(AuthService authService, JwtUtils jwtUtils, JwtProperties jwtProperties,
                          CookieUtils cookieUtils,
                          ObjectProvider<TokenBlacklistService> blacklistProvider,
                          Environment environment,
                          ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.authService = authService;
        this.jwtUtils = jwtUtils;
        this.jwtProperties = jwtProperties;
        this.cookieUtils = cookieUtils;
        this.blacklistProvider = blacklistProvider;
        this.environment = environment;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @RateLimit(maxRequests = 5, windowSeconds = 60)
    @AuditedOperation(type = "LOGIN")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletResponse response) {
        LoginResponse result;
        try {
            result = authService.login(request);
        } catch (BusinessException e) {
            recordLoginFailure(e.getCode());
            throw e;
        }
        setTokenCookies(response, result.getToken(), result.getRefreshToken());
        // Strip tokens from JSON body — they are now HttpOnly cookies only.
        // This is a security best practice: HttpOnly cookies are inaccessible to
        // JavaScript (XSS-resistant), and removing tokens from the JSON response
        // ensures they never appear in browser history/logs/localStorage.
        result.setToken(null);
        result.setRefreshToken(null);
        return ApiResponse.success(result);
    }

    @GetMapping("/userinfo")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserInfo> userInfo() {
        return ApiResponse.success(authService.getUserInfo(UserContext.getCurrentUserId()));
    }

    @AuditedOperation(type = "LOGOUT")
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> logout(HttpServletRequest request,
                                     HttpServletResponse response) {
        TokenBlacklistService svc = blacklistProvider.getIfAvailable();
        if (svc == null && isProdProfile()) {
            return ApiResponse.fail("AUTH_TOKEN_INVALID", "Token黑名单服务不可用");
        }
        String token = resolveAccessToken(request);
        if (token != null && jwtUtils.validateToken(token)) {
            if (svc != null && !svc.blacklist(token, jwtUtils.getRemainingTtlMillis(token)) && isProdProfile()) {
                return ApiResponse.fail("AUTH_TOKEN_INVALID", "Token黑名单写入失败");
            }
        }
        // M-008: Also blacklist refresh token on logout
        String refreshToken = cookieUtils.getCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE);
        if (refreshToken != null && !refreshToken.isBlank() && jwtUtils.validateToken(refreshToken)) {
            if (svc != null && !svc.blacklist(refreshToken, jwtUtils.getRemainingTtlMillis(refreshToken)) && isProdProfile()) {
                return ApiResponse.fail("AUTH_TOKEN_INVALID", "Token黑名单写入失败");
            }
        }
        cookieUtils.clearAuthCookies(response);
        UserContext.clear();
        return ApiResponse.success();
    }

    @RateLimit(maxRequests = 5, windowSeconds = 60)
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest request,
                                                HttpServletResponse response,
                                                @RequestHeader(value = "X-Refresh-Token", required = false) String headerRefreshToken) {
        // Read refresh token: cookie first, then X-Refresh-Token header (backward compat)
        String refreshToken = cookieUtils.getCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE);
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = headerRefreshToken;
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            return ApiResponse.fail("AUTH_TOKEN_INVALID", "Refresh token缺失");
        }
        if (!jwtUtils.validateToken(refreshToken) || !jwtUtils.isRefreshToken(refreshToken)) {
            return ApiResponse.fail("AUTH_TOKEN_INVALID", "Refresh token无效");
        }
        TokenBlacklistService svc = blacklistProvider.getIfAvailable();
        if (svc == null && isProdProfile()) {
            return ApiResponse.fail("AUTH_TOKEN_INVALID", "Token黑名单服务不可用");
        }
        if (svc != null && svc.isBlacklisted(refreshToken)) {
            return ApiResponse.fail("AUTH_TOKEN_INVALID", "Refresh token已失效");
        }
        Claims claims = jwtUtils.parseToken(refreshToken);
        Long userId = claims.get(JwtUtils.CLAIM_USER_ID, Long.class);
        if (svc != null && !svc.blacklist(refreshToken, jwtUtils.getRemainingTtlMillis(refreshToken)) && isProdProfile()) {
            return ApiResponse.fail("AUTH_TOKEN_INVALID", "Token黑名单写入失败");
        }
        // Blacklist old access token to prevent replay during its remaining TTL
        String oldAccessToken = resolveAccessToken(request);
        if (oldAccessToken != null && jwtUtils.validateToken(oldAccessToken)) {
            if (svc != null && !svc.blacklist(oldAccessToken, jwtUtils.getRemainingTtlMillis(oldAccessToken)) && isProdProfile()) {
                return ApiResponse.fail("AUTH_TOKEN_INVALID", "Token黑名单写入失败");
            }
        }
        LoginResponse result = authService.loginById(userId);
        setTokenCookies(response, result.getToken(), result.getRefreshToken());
        // Strip tokens from JSON body — they are now HttpOnly cookies only.
        // This is a security best practice: HttpOnly cookies are inaccessible to
        // JavaScript (XSS-resistant), and removing tokens from the JSON response
        // ensures they never appear in browser history/logs/localStorage.
        result.setToken(null);
        result.setRefreshToken(null);
        return ApiResponse.success(result);
    }

    // ---- private helpers ----

    /**
     * Set access and refresh tokens as HttpOnly, SameSite=Strict cookies.
     */
    private void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        long accessMaxAge = jwtProperties.getExpiration() / 1000;
        long refreshMaxAge = jwtProperties.getRefreshExpiration() / 1000;
        cookieUtils.setAccessTokenCookie(response, accessToken, accessMaxAge);
        cookieUtils.setRefreshTokenCookie(response, refreshToken, refreshMaxAge);
    }

    /**
     * Resolve access token: cookie first, then Authorization header (backward compat).
     */
    private String resolveAccessToken(HttpServletRequest request) {
        String cookieToken = cookieUtils.getCookieValue(request, CookieUtils.ACCESS_TOKEN_COOKIE);
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }
        String authHeader = request.getHeader(jwtProperties.getHeader());
        return extractToken(authHeader);
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) return null;
        String prefix = jwtProperties.getTokenPrefix();
        if (prefix != null && !prefix.isBlank() && authHeader.startsWith(prefix)) {
            return authHeader.substring(prefix.length()).trim();
        }
        return authHeader.trim();
    }

    private boolean isProdProfile() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    private void recordLoginFailure(String code) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        Counter.builder("auth.login.failures")
                .tag("code", metricTag(code))
                .register(registry)
                .increment();
    }

    private String metricTag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
