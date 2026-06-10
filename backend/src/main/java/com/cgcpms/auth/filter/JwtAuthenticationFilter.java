package com.cgcpms.auth.filter;

import com.cgcpms.auth.config.JwtProperties;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.result.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Validates the JWT on every request, populates {@link UserContext} and the
 * Spring Security context, and rejects invalid tokens with a 401 JSON body.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> SKIP_PATHS = List.of(
            "/auth/login",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/doc.html",
            "/webjars/**"
    );

    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtUtils jwtUtils,
                                   JwtProperties jwtProperties,
                                   ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return SKIP_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
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
        try {
            Claims claims = jwtUtils.parseToken(token);
            UserContext.set(claims);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            UserContext.getCurrentUsername(),
                            null,
                            AuthorityUtils.NO_AUTHORITIES);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
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

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.fail("AUTH_TOKEN_INVALID", "Token无效");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
