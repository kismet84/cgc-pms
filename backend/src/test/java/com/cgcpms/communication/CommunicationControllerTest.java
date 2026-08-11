package com.cgcpms.communication;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.communication.controller.CommunicationController;
import com.cgcpms.communication.service.CommunicationEventService;
import com.cgcpms.communication.service.CommunicationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunicationControllerTest {
    private final CommunicationService service = mock(CommunicationService.class);
    private final CommunicationController controller = new CommunicationController(
            service, mock(CommunicationEventService.class));

    @Test
    void routesExactlyOneCursorAndRejectsConflicts() {
        when(service.messages(7L, 11L, 50)).thenReturn(List.of());
        when(service.messages(7L, 0L, 50)).thenReturn(List.of());
        when(service.messagesBefore(7L, 13L, 50)).thenReturn(List.of());

        controller.messages(7L, 11L, null, 50);
        controller.messages(7L, null, null, 50);
        controller.messages(7L, null, 13L, 50);

        verify(service).messages(7L, 11L, 50);
        verify(service).messages(7L, 0L, 50);
        verify(service).messagesBefore(7L, 13L, 50);
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> controller.messages(7L, 11L, 13L, 50));
        assertEquals("COMMUNICATION_CURSOR_CONFLICT", conflict.getCode());
    }
}
