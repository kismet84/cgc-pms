package com.cgcpms.common.filter;

import com.cgcpms.auth.config.SecurityConfig;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.ratelimit.GlobalWriteRateLimitProperties;
import com.cgcpms.common.ratelimit.RateLimitCounterStore;
import com.cgcpms.common.result.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Global write-path rate limiting for authenticated APIs.
 */
@Component
public class GlobalWriteRateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "global-write";
    private static final List<String> SKIP_PATHS = List.of(
            SecurityConfig.AUTH_WHITELIST_PATHS,
            SecurityConfig.DOC_WHITELIST_PATHS,
            SecurityConfig.HEALTH_WHITELIST_PATHS
    ).stream().flatMap(java.util.Arrays::stream).toList();

    private final RateLimitCounterStore counterStore;
    private final GlobalWriteRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GlobalWriteRateLimitFilter(RateLimitCounterStore counterStore,
                                      GlobalWriteRateLimitProperties properties,
                                      ObjectMapper objectMapper) {
        this.counterStore = counterStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String method = request.getMethod();
        if (HttpMethod.GET.matches(method)
                || HttpMethod.HEAD.matches(method)
                || HttpMethod.OPTIONS.matches(method)) {
            return true;
        }
        String path = request.getServletPath();
        return SKIP_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = buildLimitKey(request);
        long count = counterStore.increment(key, properties.getWindowSeconds());
        if (count > properties.getMaxRequests()) {
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String buildLimitKey(HttpServletRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId != null) {
            return KEY_PREFIX + ":user:" + userId;
        }
        return KEY_PREFIX + ":ip:" + resolveClientIp(request);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.fail(
                "RATE_LIMIT_EXCEEDED",
                String.format("请求过于频繁，请在 %d 秒后重试", properties.getWindowSeconds()));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
