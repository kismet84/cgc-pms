package com.cgcpms.file.audit;

import com.cgcpms.audit.event.OperationAuditEvent;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.invoice.vo.InvoiceRecognizeResultVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
@RequiredArgsConstructor
public class InvoiceRecognitionAuditAspect {

    private static final String BUSINESS_TYPE = "INVOICE";

    private final ApplicationEventPublisher publisher;

    @Around("execution(* com.cgcpms.invoice.controller.InvoiceController.recognize(..))")
    public Object auditRecognition(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            InvoiceRecognizeResultVO data = extractRecognitionResult(result);
            boolean success = data != null && Boolean.TRUE.equals(data.getSuccess());
            publish("INVOICE_RECOGNITION", null, success,
                    success ? null : errorTag(data != null ? data.getErrorCode() : null),
                    elapsed(start));
            return result;
        } catch (Throwable throwable) {
            publish("INVOICE_RECOGNITION", null, false, errorTag(throwable), elapsed(start));
            throw throwable;
        }
    }

    @Around("execution(* com.cgcpms.invoice.controller.InvoiceController.create(..))"
            + " || execution(* com.cgcpms.invoice.controller.InvoiceController.register(..))")
    public Object auditManualConfirmation(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            publish("INVOICE_MANUAL_CONFIRM", extractBusinessId(result), true, null, elapsed(start));
            return result;
        } catch (Throwable throwable) {
            publish("INVOICE_MANUAL_CONFIRM", null, false, errorTag(throwable), elapsed(start));
            throw throwable;
        }
    }

    private InvoiceRecognizeResultVO extractRecognitionResult(Object result) {
        if (result instanceof ApiResponse<?> response
                && response.getData() instanceof InvoiceRecognizeResultVO recognizeResult) {
            return recognizeResult;
        }
        return null;
    }

    private String extractBusinessId(Object result) {
        if (result instanceof ApiResponse<?> response && response.getData() != null) {
            return String.valueOf(response.getData());
        }
        return null;
    }

    private void publish(String operationType, String businessId, boolean successFlag, String errorCode, int durationMs) {
        HttpServletRequest request = resolveRequest();
        OperationAuditEvent event = OperationAuditEvent.builder()
                .tenantId(UserContext.getCurrentTenantId())
                .userId(UserContext.getCurrentUserId())
                .operationType(operationType)
                .businessType(BUSINESS_TYPE)
                .businessId(businessId)
                .httpMethod(request != null ? request.getMethod() : null)
                .requestPath(request != null ? request.getRequestURI() : null)
                .successFlag(successFlag)
                .errorCode(errorCode)
                .sourceIp(resolveClientIp(request))
                .durationMs(durationMs)
                .createdAt(LocalDateTime.now())
                .build();
        publisher.publishEvent(event);
    }

    private HttpServletRequest resolveRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Real-IP");
        if (hasClientIp(ip)) {
            return ip.trim();
        }
        ip = request.getHeader("X-Forwarded-For");
        if (hasClientIp(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean hasClientIp(String ip) {
        return ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip);
    }

    private String errorTag(Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            return errorTag(businessException.getCode());
        }
        return throwable.getClass().getSimpleName();
    }

    private String errorTag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private int elapsed(long start) {
        long duration = System.currentTimeMillis() - start;
        return duration > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) duration;
    }
}
