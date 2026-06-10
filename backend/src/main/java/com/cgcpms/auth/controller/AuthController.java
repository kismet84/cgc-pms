package com.cgcpms.auth.controller;

import com.cgcpms.auth.config.JwtProperties;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.dto.LoginRequest;
import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.dto.UserInfo;
import com.cgcpms.auth.service.AuthService;
import com.cgcpms.auth.service.TokenBlacklistService;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.result.ApiResponse;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final ObjectProvider<TokenBlacklistService> blacklistProvider;

    public AuthController(AuthService authService, JwtUtils jwtUtils, JwtProperties jwtProperties,
                          ObjectProvider<TokenBlacklistService> blacklistProvider) {
        this.authService = authService;
        this.jwtUtils = jwtUtils;
        this.jwtProperties = jwtProperties;
        this.blacklistProvider = blacklistProvider;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/userinfo")
    public ApiResponse<UserInfo> userInfo() {
        return ApiResponse.success(authService.getUserInfo(UserContext.getCurrentUserId()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        if (token != null && jwtUtils.validateToken(token)) {
            TokenBlacklistService svc = blacklistProvider.getIfAvailable();
            if (svc != null) svc.blacklist(token, jwtUtils.getRemainingTtlMillis(token));
        }
        UserContext.clear();
        return ApiResponse.success();
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestHeader("X-Refresh-Token") String refreshToken) {
        if (!jwtUtils.validateToken(refreshToken) || !jwtUtils.isRefreshToken(refreshToken)) {
            return ApiResponse.fail("AUTH_TOKEN_INVALID", "Refresh token无效");
        }
        TokenBlacklistService svc = blacklistProvider.getIfAvailable();
        if (svc != null && svc.isBlacklisted(refreshToken)) {
            return ApiResponse.fail("AUTH_TOKEN_INVALID", "Refresh token已失效");
        }
        Claims claims = jwtUtils.parseToken(refreshToken);
        Long userId = claims.get(JwtUtils.CLAIM_USER_ID, Long.class);
        if (svc != null) svc.blacklist(refreshToken, jwtUtils.getRemainingTtlMillis(refreshToken));
        return ApiResponse.success(authService.loginById(userId));
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) return null;
        String prefix = jwtProperties.getTokenPrefix();
        if (prefix != null && !prefix.isBlank() && authHeader.startsWith(prefix)) {
            return authHeader.substring(prefix.length()).trim();
        }
        return authHeader.trim();
    }
}
