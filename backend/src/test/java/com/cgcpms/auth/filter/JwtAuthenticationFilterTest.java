package com.cgcpms.auth.filter;

import com.cgcpms.auth.config.JwtProperties;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.service.AuthService;
import com.cgcpms.auth.service.TokenBlacklistService;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
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
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @DisplayName("local profile allows explicitly disabled blacklist service")
    void localAllowsExplicitlyDisabledBlacklistService() throws Exception {
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

        MockEnvironment localEnvironment = env("local");
        localEnvironment.setProperty("auth.token-blacklist.enabled", "false");
        ExposedJwtAuthenticationFilter localFilter = new ExposedJwtAuthenticationFilter(
                jwtUtils, jwtProperties, cookieUtils, new ObjectMapper(), blacklistProvider, localEnvironment);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.setServletPath("/protected");
        request.addHeader("Authorization", "Bearer local-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        localFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(1L, request.getAttribute("accessLog.userId"));
        assertEquals(0L, request.getAttribute("accessLog.tenantId"));
    }

    @Test
    @DisplayName("decoded compact permissions keep every authority")
    void compactPermissionClaimBuildsAuthorities() throws Exception {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        JwtProperties jwtProperties = mock(JwtProperties.class);
        CookieUtils cookieUtils = mock(CookieUtils.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TokenBlacklistService> blacklistProvider = mock(ObjectProvider.class);
        TokenBlacklistService blacklistService = mock(TokenBlacklistService.class);
        FilterChain chain = mock(FilterChain.class);

        when(jwtProperties.getHeader()).thenReturn("Authorization");
        when(jwtProperties.getTokenPrefix()).thenReturn("Bearer ");
        when(cookieUtils.getCookieValue(any(HttpServletRequest.class), eq(CookieUtils.ACCESS_TOKEN_COOKIE))).thenReturn(null);
        when(jwtUtils.validateToken("compact-token")).thenReturn(true);
        when(jwtUtils.isRefreshToken("compact-token")).thenReturn(false);
        List<String> compressedPermissions = IntStream.range(0, 200)
                .mapToObj(index -> "measurement:operation:" + index)
                .toList();
        when(jwtUtils.parseToken("compact-token")).thenReturn(Jwts.claims()
                .add(JwtUtils.CLAIM_USER_ID, 1L)
                .add(JwtUtils.CLAIM_USERNAME, "admin")
                .add(JwtUtils.CLAIM_TENANT_ID, 0L)
                .add(JwtUtils.CLAIM_ROLES, List.of("SUPER_ADMIN"))
                .add(JwtUtils.CLAIM_PERMISSIONS, JwtUtils.encodePermissionClaim(compressedPermissions))
                .build());
        when(blacklistProvider.getIfAvailable()).thenReturn(blacklistService);
        when(blacklistService.isBlacklisted("compact-token")).thenReturn(false);
        doAnswer(invocation -> {
            var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
            assertTrue(authorities.stream().anyMatch(value -> value.getAuthority().equals("ROLE_SUPER_ADMIN")));
            assertTrue(authorities.stream().anyMatch(value -> value.getAuthority().equals("measurement:operation:0")));
            assertTrue(authorities.stream().anyMatch(value -> value.getAuthority().equals("measurement:operation:199")));
            return null;
        }).when(chain).doFilter(any(), any());

        ExposedJwtAuthenticationFilter compactFilter = new ExposedJwtAuthenticationFilter(
                jwtUtils, jwtProperties, cookieUtils, new ObjectMapper(), blacklistProvider, env("local"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.setServletPath("/protected");
        request.addHeader("Authorization", "Bearer compact-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        compactFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("rejects access tokens after the server authorization snapshot changes")
    void staleAuthorizationSnapshotIsRejected() throws Exception {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        JwtProperties jwtProperties = mock(JwtProperties.class);
        CookieUtils cookieUtils = mock(CookieUtils.class);
        AuthService authService = mock(AuthService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TokenBlacklistService> blacklistProvider = mock(ObjectProvider.class);
        TokenBlacklistService blacklistService = mock(TokenBlacklistService.class);
        FilterChain chain = mock(FilterChain.class);
        var claims = Jwts.claims()
                .add(JwtUtils.CLAIM_USER_ID, 1L)
                .add(JwtUtils.CLAIM_TENANT_ID, 902L)
                .build();

        when(jwtProperties.getHeader()).thenReturn("Authorization");
        when(jwtProperties.getTokenPrefix()).thenReturn("Bearer ");
        when(cookieUtils.getCookieValue(any(HttpServletRequest.class), eq(CookieUtils.ACCESS_TOKEN_COOKIE))).thenReturn(null);
        when(jwtUtils.validateToken("stale-token")).thenReturn(true);
        when(jwtUtils.isRefreshToken("stale-token")).thenReturn(false);
        when(jwtUtils.parseToken("stale-token")).thenReturn(claims);
        when(blacklistProvider.getIfAvailable()).thenReturn(blacklistService);
        when(blacklistService.isBlacklisted("stale-token")).thenReturn(false);
        when(authService.isCurrentAuthorization(claims)).thenAnswer(invocation -> {
            assertEquals(902L, UserContext.getCurrentTenantId());
            return false;
        });

        AuthorizationAwareFilter staleFilter = new AuthorizationAwareFilter(
                jwtUtils, jwtProperties, cookieUtils, blacklistProvider, authService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.setServletPath("/protected");
        request.addHeader("Authorization", "Bearer stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        staleFilter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("credential snapshot lookup is bound to token tenant and user")
    void credentialSnapshotLookupUsesExplicitTenantAndUser() {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setTenantId(1001L);
        user.setPassword("encoded-password");
        user.setStatus("ENABLE");
        var claims = Jwts.claims()
                .add(JwtUtils.CLAIM_USER_ID, 7L)
                .add(JwtUtils.CLAIM_TENANT_ID, 1001L)
                .add(JwtUtils.CLAIM_CREDENTIAL_VERSION, "current-version")
                .build();
        when(userMapper.selectCredentialByTenantAndId(1001L, 7L)).thenReturn(user);
        when(jwtUtils.credentialVersion("encoded-password")).thenReturn("current-version");

        CredentialAwareFilter credentialFilter = new CredentialAwareFilter(jwtUtils, userMapper);

        assertTrue(credentialFilter.currentCredential(claims));
        verify(userMapper).selectCredentialByTenantAndId(1001L, 7L);
    }

    private static class ExposedJwtAuthenticationFilter extends JwtAuthenticationFilter {
        ExposedJwtAuthenticationFilter() {
            super(null, null, null, null, null, null, null, new MockEnvironment());
        }

        ExposedJwtAuthenticationFilter(JwtUtils jwtUtils,
                                       JwtProperties jwtProperties,
                                       CookieUtils cookieUtils,
                                       ObjectMapper objectMapper,
                                       ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider,
                                       MockEnvironment environment) {
            super(jwtUtils, jwtProperties, cookieUtils, objectMapper, tokenBlacklistServiceProvider,
                    permissiveUserMapper(), authServiceProvider(mock(AuthService.class)), environment);
        }

        private static SysUserMapper permissiveUserMapper() {
            return mock(SysUserMapper.class);
        }

        boolean shouldSkip(HttpServletRequest request) {
            return shouldNotFilter(request);
        }

        boolean shouldSkipAsyncDispatch() {
            return shouldNotFilterAsyncDispatch();
        }

        @Override
        protected boolean isCurrentCredential(io.jsonwebtoken.Claims claims) {
            return true;
        }

        @Override
        protected boolean isCurrentAuthorization(io.jsonwebtoken.Claims claims) {
            return true;
        }
    }

    private static class AuthorizationAwareFilter extends JwtAuthenticationFilter {
        AuthorizationAwareFilter(JwtUtils jwtUtils,
                                 JwtProperties jwtProperties,
                                 CookieUtils cookieUtils,
                                 ObjectProvider<TokenBlacklistService> blacklistProvider,
                                 AuthService authService) {
            super(jwtUtils, jwtProperties, cookieUtils, new ObjectMapper(), blacklistProvider,
                    mock(SysUserMapper.class), authServiceProvider(authService), env("local"));
        }

        @Override
        protected boolean isCurrentCredential(io.jsonwebtoken.Claims claims) {
            return true;
        }
    }

    private static class CredentialAwareFilter extends JwtAuthenticationFilter {
        CredentialAwareFilter(JwtUtils jwtUtils, SysUserMapper userMapper) {
            super(jwtUtils, mock(JwtProperties.class), mock(CookieUtils.class), new ObjectMapper(),
                    mockProvider(), userMapper, authServiceProvider(mock(AuthService.class)), env("local"));
        }

        boolean currentCredential(io.jsonwebtoken.Claims claims) {
            return isCurrentCredential(claims);
        }

        @SuppressWarnings("unchecked")
        private static ObjectProvider<TokenBlacklistService> mockProvider() {
            return mock(ObjectProvider.class);
        }
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuthService> authServiceProvider(AuthService authService) {
        ObjectProvider<AuthService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(authService);
        return provider;
    }

    private static MockEnvironment env(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
