package com.cgcpms.auth.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.auth.config.JwtProperties;
import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.service.AuthService;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile({"dev", "local"})
@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(name = "auth.dev-login.enabled", havingValue = "true")
public class DevAuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final CookieUtils cookieUtils;
    private final String defaultUsername;

    public DevAuthController(AuthService authService,
                             JwtProperties jwtProperties,
                             CookieUtils cookieUtils,
                             @Value("${auth.dev-login.default-username}") String defaultUsername) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
        this.cookieUtils = cookieUtils;
        this.defaultUsername = defaultUsername;
    }

    @AuditedOperation(type = "LOGIN")
    @GetMapping("/dev-login")
    public ResponseEntity<?> devLogin(@RequestParam(required = false) String username,
                                      @RequestParam(required = false) String redirect,
                                      HttpServletResponse response) {
        String effectiveUsername = resolveUsername(username);
        LoginResponse result = authService.loginByUsernameEnsuringDevAccount(effectiveUsername, defaultUsername);
        setTokenCookies(response, result.getToken(), result.getRefreshToken());
        result.setToken(null);
        result.setRefreshToken(null);

        String redirectTarget = normalizeRedirect(redirect);
        if (redirectTarget != null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, redirectTarget)
                    .build();
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private String resolveUsername(String username) {
        if (StringUtils.hasText(username)) {
            return username.trim();
        }
        if (StringUtils.hasText(defaultUsername)) {
            return defaultUsername.trim();
        }
        throw new BusinessException("DEV_LOGIN_USER_REQUIRED", "缺少 dev login 用户名");
    }

    private String normalizeRedirect(String redirect) {
        if (!StringUtils.hasText(redirect)) {
            return null;
        }
        String target = redirect.trim();
        if (!target.startsWith("/") || target.startsWith("//")) {
            return "/";
        }
        return target;
    }

    private void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        long accessMaxAge = jwtProperties.getExpiration() / 1000;
        long refreshMaxAge = jwtProperties.getRefreshExpiration() / 1000;
        cookieUtils.setAccessTokenCookie(response, accessToken, accessMaxAge);
        cookieUtils.setRefreshTokenCookie(response, refreshToken, refreshMaxAge);
    }
}
