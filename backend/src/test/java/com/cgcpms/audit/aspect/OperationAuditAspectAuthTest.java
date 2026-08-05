package com.cgcpms.audit.aspect;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.audit.event.OperationAuditEvent;
import com.cgcpms.auth.dto.LoginRequest;
import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.dto.UserInfo;
import com.cgcpms.common.result.ApiResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Authentication operation audit")
class OperationAuditAspectAuthTest {

    @Test
    @DisplayName("successful login audit uses request tenant and authenticated user")
    void successfulLoginUsesRequestTenantAndAuthenticatedUser() throws Throwable {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = AuditFixture.class.getDeclaredMethod("login", LoginRequest.class);
        AuditedOperation annotation = method.getAnnotation(AuditedOperation.class);
        LoginRequest request = new LoginRequest();
        request.setTenantId(1001L);
        request.setUsername("same-name");
        UserInfo userInfo = UserInfo.builder().userId("44").username("same-name").build();
        ApiResponse<LoginResponse> response = ApiResponse.success(
                new LoginResponse("access", "refresh", userInfo));
        when(joinPoint.proceed()).thenReturn(response);
        when(joinPoint.getArgs()).thenReturn(new Object[]{request});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.toShortString()).thenReturn("AuditFixture.login(..)");
        assertEquals("LOGIN", annotation.type());
        assertEquals(1001L, ((LoginRequest) joinPoint.getArgs()[0]).getTenantId());

        new OperationAuditAspect(publisher).around(joinPoint, annotation);

        ArgumentCaptor<OperationAuditEvent> event = ArgumentCaptor.forClass(OperationAuditEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals(1001L, event.getValue().tenantId());
        assertEquals(44L, event.getValue().userId());
        assertEquals("LOGIN", event.getValue().operationType());
    }

    private static final class AuditFixture {
        @AuditedOperation(type = "LOGIN")
        void login(LoginRequest request) {
        }
    }
}
