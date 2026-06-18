package com.cgcpms.auth.filter;

import com.cgcpms.auth.config.JwtProperties;
import com.cgcpms.auth.config.SecurityConfig;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.service.TokenBlacklistService;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.result.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates the JWT on every request, populates {@link UserContext} and the
 * Spring Security context, and rejects invalid tokens with a 401 JSON body.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> SKIP_PATHS = List.of(SecurityConfig.WHITELIST_PATHS);

    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final CookieUtils cookieUtils;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtUtils jwtUtils,
                                   JwtProperties jwtProperties,
                                   CookieUtils cookieUtils,
                                   ObjectMapper objectMapper,
                                   ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider) {
        this.jwtUtils = jwtUtils;
        this.jwtProperties = jwtProperties;
        this.cookieUtils = cookieUtils;
        this.objectMapper = objectMapper;
        this.tokenBlacklistServiceProvider = tokenBlacklistServiceProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return SKIP_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            writeUnauthorized(response);
            return;
        }
        if (!jwtUtils.validateToken(token)) {
            writeUnauthorized(response);
            return;
        }
        // Reject refresh tokens used as API access tokens
        if (jwtUtils.isRefreshToken(token)) {
            writeUnauthorized(response);
            return;
        }
        // Check blacklist
        TokenBlacklistService blacklistService = tokenBlacklistServiceProvider.getIfAvailable();
        if (blacklistService != null && blacklistService.isBlacklisted(token)) {
            writeUnauthorized(response);
            return;
        }
        try {
            Claims claims = jwtUtils.parseToken(token);
            UserContext.set(claims);
            
            List<GrantedAuthority> authorities = buildAuthorities(claims);
            
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            UserContext.getCurrentUsername(),
                            null,
                            authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        // Cookie takes precedence (HttpOnly mode)
        String cookieToken = cookieUtils.getCookieValue(request, CookieUtils.ACCESS_TOKEN_COOKIE);
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }
        // Fall back to Authorization header (backward compat)
        String headerValue = request.getHeader(jwtProperties.getHeader());
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String prefix = jwtProperties.getTokenPrefix();
        if (prefix != null && !prefix.isBlank() && headerValue.startsWith(prefix)) {
            return headerValue.substring(prefix.length()).trim();
        }
        return headerValue.trim();
    }

    /**
     * Build Spring Security authorities from JWT claims.
     * Role codes become ROLE_ authorities; permission codes become direct authorities.
     */
    private List<GrantedAuthority> buildAuthorities(Claims claims) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        
        // Extract role codes, prefix with ROLE_
        List<String> roleCodes = claims.get(JwtUtils.CLAIM_ROLES, List.class);
        if (roleCodes != null) {
            for (String roleCode : roleCodes) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
            }
        }
        
        // Extract permissions (e.g., "system:user:add")
        List<String> permissions = claims.get(JwtUtils.CLAIM_PERMISSIONS, List.class);
        if (permissions != null) {
            for (String perm : permissions) {
                authorities.add(new SimpleGrantedAuthority(perm));
            }
        }
        
        return authorities.isEmpty() ? Collections.emptyList() : authorities;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.fail("AUTH_TOKEN_INVALID", "Token无效");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
