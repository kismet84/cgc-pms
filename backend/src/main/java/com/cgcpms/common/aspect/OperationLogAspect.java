package com.cgcpms.common.aspect;

import com.cgcpms.common.annotation.OperationLog;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.context.TraceIdContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Records execution of methods annotated with {@link OperationLog}.
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String method = signature.getDeclaringTypeName() + "." + signature.getName();
        String operation = operationLog.value();
        String traceId = TraceIdContext.get();
        String ip = resolveClientIp();
        String operator = resolveOperator();

        Object result = null;
        boolean success = true;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            success = false;
            error = t;
            throw t;
        } finally {
            long duration = System.currentTimeMillis() - start;
            String params = operationLog.saveParams() ? safeArgs(joinPoint.getArgs()) : "<omitted>";
            String resultText = operationLog.saveResult() ? String.valueOf(result) : "<omitted>";
            if (success) {
                log.info("operationLog operator={} operation=\"{}\" method={} ip={} traceId={} durationMs={} params={} result={}",
                        operator, operation, method, ip, traceId, duration, params, resultText);
            } else {
                log.warn("operationLog operator={} operation=\"{}\" method={} ip={} traceId={} durationMs={} params={} error={}",
                        operator, operation, method, ip, traceId, duration, params,
                        error == null ? "unknown" : error.getMessage());
            }
        }
    }

    private String safeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        try {
            return Arrays.stream(args)
                    .map(arg -> {
                        if (arg == null) return "null";
                        String s = arg.toString();
                        // Mask password/token/secret fields that may appear in toString()
                        return s.replaceAll("(?i)(password|secret|token|accessKey|secretKey|phone|email|bankAccount|creditCode|contactPhone|mobile|idCard|身份证|手机号|银行卡|密码|令牌)=[^,}\\]]+", "$1=***");
                    })
                    .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        } catch (Exception e) {
            return "<unprintable>";
        }
    }

    private String resolveOperator() {
        String username = UserContext.getCurrentUsername();
        return username != null ? username : "anonymous";
    }

    private String resolveClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
