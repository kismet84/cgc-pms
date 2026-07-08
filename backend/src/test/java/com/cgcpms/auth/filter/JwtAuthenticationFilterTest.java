package com.cgcpms.auth.filter;

import com.cgcpms.auth.config.JwtProperties;
import com.cgcpms.auth.service.TokenBlacklistService;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("JWT authentication filter")
@ExtendWith(OutputCaptureExtension.class)
class JwtAuthenticationFilterTest {

    private final ExposedJwtAuthenticationFilter filter = new ExposedJwtAuthenticationFilter();

    @Test
    @DisplayName("does not skip notification stream initial requests")
    void doesNotSkipNotificationStreamInitialRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/notifications/stream");
        request.setServletPath("/notifications/stream");

        assertFalse(filter.shouldSkip(request));
    }

    @Test
    @DisplayName("participates in async dispatches")
    void participatesInAsyncDispatches() {
        assertFalse(filter.shouldSkipAsyncDispatch());
    }

    @Test
    @DisplayName("prod profile rejects requests when token blacklist service is unavailable")
    void prodRejectsWhenBlacklistServiceUnavailable(CapturedOutput output) throws Exception {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        JwtProperties jwtProperties = mock(JwtProperties.class);
        CookieUtils cookieUtils = mock(CookieUtils.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TokenBlacklistService> blacklistProvider = mock(ObjectProvider.class);
        FilterChain chain = mock(FilterChain.class);

        when(jwtProperties.getHeader()).thenReturn("Authorization");
        when(jwtProperties.getTokenPrefix()).thenReturn("Bearer ");
        when(cookieUtils.getCookieValue(any(HttpServletRequest.class), eq(CookieUtils.ACCESS_TOKEN_COOKIE))).thenReturn(null);
        when(jwtUtils.validateToken("prod-token")).thenReturn(true);
        when(jwtUtils.isRefreshToken("prod-token")).thenReturn(false);
        when(jwtUtils.parseToken("prod-token")).thenReturn(Jwts.claims()
                .add(JwtUtils.CLAIM_USER_ID, 1L)
                .add(JwtUtils.CLAIM_USERNAME, "admin")
                .add(JwtUtils.CLAIM_TENANT_ID, 0L)
                .add(JwtUtils.CLAIM_ROLES, List.of("ADMIN"))
                .add(JwtUtils.CLAIM_PERMISSIONS, List.of())
                .build());
        when(blacklistProvider.getIfAvailable()).thenReturn(null);

        ExposedJwtAuthenticationFilter prodFilter = new ExposedJwtAuthenticationFilter(
                jwtUtils, jwtProperties, cookieUtils, new ObjectMapper(), blacklistProvider, env("prod"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.setServletPath("/protected");
        request.addHeader("Authorization", "Bearer prod-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        prodFilter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        org.junit.jupiter.api.Assertions.assertEquals(401, response.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(output.getOut().contains("BLACKLIST_UNAVAILABLE"));
        org.junit.jupiter.api.Assertions.assertFalse(output.getOut().contains("redis://"));
        org.junit.jupiter.api.Assertions.assertFalse(output.getOut().contains("REDIS_PASSWORD"));
    }

    @Test
    @DisplayName("local profile still allows explicit blacklist-service fallback")
    void localAllowsBlacklistServiceFallback() throws Exception {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        JwtProperties jwtProperties = mock(JwtProperties.class);
        CookieUtils cookieUtils = mock(CookieUtils.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TokenBlacklistService> blacklistProvider = mock(ObjectProvider.class);
        FilterChain chain = mock(FilterChain.class);

        when(jwtProperties.getHeader()).thenReturn("Authorization");
        when(jwtProperties.getTokenPrefix()).thenReturn("Bearer ");
        when(cookieUtils.getCookieValue(any(HttpServletRequest.class), eq(CookieUtils.ACCESS_TOKEN_COOKIE))).thenReturn(null);
        when(jwtUtils.validateToken("local-token")).thenReturn(true);
        when(jwtUtils.isRefreshToken("local-token")).thenReturn(false);
        when(jwtUtils.parseToken("local-token")).thenReturn(Jwts.claims()
                .add(JwtUtils.CLAIM_USER_ID, 1L)
                .add(JwtUtils.CLAIM_USERNAME, "admin")
                .add(JwtUtils.CLAIM_TENANT_ID, 0L)
                .add(JwtUtils.CLAIM_ROLES, List.of("ADMIN"))
                .add(JwtUtils.CLAIM_PERMISSIONS, List.of())
                .build());
        when(blacklistProvider.getIfAvailable()).thenReturn(null);

        ExposedJwtAuthenticationFilter localFilter = new ExposedJwtAuthenticationFilter(
                jwtUtils, jwtProperties, cookieUtils, new ObjectMapper(), blacklistProvider, env("local"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.setServletPath("/protected");
        request.addHeader("Authorization", "Bearer local-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        localFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private static class ExposedJwtAuthenticationFilter extends JwtAuthenticationFilter {
        ExposedJwtAuthenticationFilter() {
            super(null, null, null, null, null, new MockEnvironment());
        }

        ExposedJwtAuthenticationFilter(JwtUtils jwtUtils,
                                       JwtProperties jwtProperties,
                                       CookieUtils cookieUtils,
                                       ObjectMapper objectMapper,
                                       ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider,
                                       MockEnvironment environment) {
            super(jwtUtils, jwtProperties, cookieUtils, objectMapper, tokenBlacklistServiceProvider, environment);
        }

        boolean shouldSkip(HttpServletRequest request) {
            return shouldNotFilter(request);
        }

        boolean shouldSkipAsyncDispatch() {
            return shouldNotFilterAsyncDispatch();
        }
    }

    private static MockEnvironment env(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
