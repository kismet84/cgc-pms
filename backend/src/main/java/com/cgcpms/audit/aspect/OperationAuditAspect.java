package com.cgcpms.audit.aspect;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.audit.event.OperationAuditEvent;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.result.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;

/**
 * 操作审计切面 — 在 @AuditedOperation 标注的方法中提取审计信息，finally 块中发布事件。
 */
@Aspect
@Slf4j
@Component
public class OperationAuditAspect {

    private final ApplicationEventPublisher publisher;
    private final ExpressionParser spelParser = new SpelExpressionParser();

    public OperationAuditAspect(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Around("@annotation(auditedOperation)")
    public Object around(ProceedingJoinPoint joinPoint, AuditedOperation auditedOperation) throws Throwable {
        long start = System.currentTimeMillis();
        String errorCode = null;
        boolean successFlag = false;
        try {
            Object result = joinPoint.proceed();
            successFlag = isSuccess(result);
            return result;
        } catch (Throwable t) {
            errorCode = t.getClass().getSimpleName();
            successFlag = false;
            throw t;
        } finally {
            int durationMs = (int) (System.currentTimeMillis() - start);
            publishEvent(joinPoint, auditedOperation, successFlag, errorCode, durationMs);
        }
    }

    private boolean isSuccess(Object result) {
        if (result instanceof ApiResponse<?> apiResp) {
            return ApiResponse.SUCCESS_CODE.equals(apiResp.getCode());
        }
        return true;
    }

    private void publishEvent(ProceedingJoinPoint joinPoint, AuditedOperation annotation,
                               boolean successFlag, String errorCode, int durationMs) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            HttpServletRequest request = resolveRequest();

            String businessId = resolveBusinessId(joinPoint, method, annotation.businessIdExpression());

            OperationAuditEvent event = OperationAuditEvent.builder()
                    .tenantId(UserContext.getCurrentTenantId())
                    .userId(UserContext.getCurrentUserId())
                    .operationType(annotation.type())
                    .businessType(annotation.businessType().isEmpty() ? null : annotation.businessType())
                    .businessId(businessId)
                    .httpMethod(request != null ? request.getMethod() : null)
                    .requestPath(resolveRequestPath(request, joinPoint))
                    .successFlag(successFlag)
                    .errorCode(errorCode)
                    .sourceIp(resolveClientIp(request))
                    .durationMs(durationMs)
                    .createdAt(LocalDateTime.now())
                    .build();

            publisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Operation audit publish failed: type={}, businessType={}, errorCode={}",
                    annotation.type(), annotation.businessType(), errorCode, e);
        }
    }

    private String resolveBusinessId(ProceedingJoinPoint joinPoint, Method method, String expr) {
        if (expr == null || expr.isEmpty()) {
            return null;
        }
        try {
            SimpleEvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            Parameter[] params = method.getParameters();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < params.length; i++) {
                ctx.setVariable(params[i].getName(), args[i]);
            }
            ctx.setVariable("args", args);
            Object value = spelParser.parseExpression(expr).getValue(ctx);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveRequestPath(HttpServletRequest request, ProceedingJoinPoint joinPoint) {
        if (request != null) {
            return request.getRequestURI();
        }
        return joinPoint.getSignature().toShortString();
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
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

    private HttpServletRequest resolveRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes sa) {
            return sa.getRequest();
        }
        return null;
    }
}
