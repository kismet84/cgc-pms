package com.cgcpms.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
