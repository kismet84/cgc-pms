package com.cgcpms.common.exception;

import io.sentry.Sentry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

class GlobalExceptionHandlerTest {

    @Test
    void hiddenResourcesReturnOneNotFoundEnvelope() {
        var response = new GlobalExceptionHandler().handleBusinessException(
                new BusinessException("COMMUNICATION_NOT_FOUND", "会话不存在"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().getCode());
    }

    @Test
    void communicationMembershipAndMessageMissesShareOneExternalNotFound() {
        var handler = new GlobalExceptionHandler();

        for (String code : new String[]{"COMMUNICATION_MEMBER_NOT_FOUND", "COMMUNICATION_MESSAGE_NOT_FOUND",
                "FILE_NOT_FOUND", "FILE_BIZ_OBJ_NOT_FOUND"}) {
            var response = handler.handleBusinessException(new BusinessException(code, "内部细节"));
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals("RESOURCE_NOT_FOUND", response.getBody().getCode());
            assertEquals("资源不存在", response.getBody().getMessage());
        }
    }

    @Test
    void unhandledUniqueConflictsReturnStableDataConflict() {
        var response = new GlobalExceptionHandler().handleDataIntegrity(
                new DataIntegrityViolationException("duplicate key"));

        assertEquals("DATA_CONFLICT", response.getCode());
        assertEquals("数据冲突，请刷新后重试", response.getMessage());
    }

    @Test
    void paymentOptimisticLockConflictsReturn409WithStableCode() {
        var response = new GlobalExceptionHandler().handleBusinessException(
                new BusinessException("PAY_APP_STATUS_CONFLICT", "付款申请已被其他用户修改，请刷新后重试"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("PAY_APP_STATUS_CONFLICT", response.getBody().getCode());
    }

    @Test
    void unexpectedExceptionsAreCapturedBySentryBeforeReturningStableEnvelope() {
        var exception = new IllegalStateException("test failure");

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            var response = new GlobalExceptionHandler().handleException(exception);

            sentry.verify(() -> Sentry.captureException(exception));
            assertEquals("SYSTEM_ERROR", response.getCode());
            assertEquals("系统异常，请稍后重试", response.getMessage());
        }
    }

    @Test
    void monitoringFailureDoesNotReplaceStableSystemErrorEnvelope() {
        var exception = new IllegalStateException("original failure");
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            sentry.when(() -> Sentry.captureException(exception))
                    .thenThrow(new IllegalStateException("SDK callback failed"));

            var response = new GlobalExceptionHandler().handleException(exception);

            assertEquals("SYSTEM_ERROR", response.getCode());
            assertEquals("系统异常，请稍后重试", response.getMessage());
        }
    }
}
