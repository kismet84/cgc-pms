package com.cgcpms.common.filter;

import com.cgcpms.common.context.TraceIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a trace id for every request, exposes it via MDC, the
 * {@link TraceIdContext} ThreadLocal and the {@code X-Trace-Id} response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        Throwable failure = null;
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        } else {
            traceId = traceId.trim();
        }
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        } else {
            requestId = requestId.trim();
        }
        try {
            MDC.put(MDC_KEY, traceId);
            MDC.put(REQUEST_ID_MDC_KEY, requestId);
            TraceIdContext.set(traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            response.setHeader(REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            long duration = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("HTTP_ACCESS traceId={} requestId={} method={} path={} projectId={} status={} duration={} exception={} clientIp={}",
                    traceId,
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    resolveProjectId(request),
                    response.getStatus(),
                    duration,
                    failure == null ? "-" : failure.getClass().getSimpleName(),
                    resolveClientIp(request));
            MDC.remove(MDC_KEY);
            MDC.remove(REQUEST_ID_MDC_KEY);
            TraceIdContext.clear();
        }
    }

    private String resolveProjectId(HttpServletRequest request) {
        String projectId = request.getParameter("projectId");
        if (projectId != null && !projectId.isBlank()) {
            return projectId.trim();
        }
        Object uriVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (uriVariables instanceof Map<?, ?> vars) {
            Object value = vars.get("projectId");
            if (value != null) {
                String candidate = value.toString().trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        String path = request.getRequestURI();
        int marker = path.indexOf("/projects/");
        if (marker >= 0) {
            int start = marker + "/projects/".length();
            int end = path.indexOf('/', start);
            String candidate = (end >= 0 ? path.substring(start, end) : path.substring(start)).trim();
            if (!candidate.isEmpty() && candidate.chars().allMatch(Character::isDigit)) {
                return candidate;
            }
        }
        return "-";
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
}
