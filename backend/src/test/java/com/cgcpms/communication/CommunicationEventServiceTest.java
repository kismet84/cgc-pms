package com.cgcpms.communication;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.communication.service.CommunicationEventService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommunicationEventServiceTest {

    @Test
    void limitsEachTenantUserToFiveStreams() {
        CommunicationEventService service = new CommunicationEventService();
        for (int index = 0; index < 5; index++) service.subscribe(7, 11);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.subscribe(7, 11));

        assertEquals("COMMUNICATION_CONNECTION_LIMIT", error.getCode());
    }

    @Test
    void replacesReloadedTabWithoutConsumingAnotherSlot() {
        CommunicationEventService service = new CommunicationEventService();
        for (int index = 0; index < 10; index++) service.subscribe(7, 11, "same-tab");
        for (int index = 1; index < 5; index++) service.subscribe(7, 11, "tab-" + index);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.subscribe(7, 11, "sixth-tab"));

        assertEquals("COMMUNICATION_CONNECTION_LIMIT", error.getCode());
    }

    @Test
    void rejectsInvalidClientId() {
        CommunicationEventService service = new CommunicationEventService();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.subscribe(7, 11, "invalid client"));

        assertEquals("COMMUNICATION_CLIENT_ID_INVALID", error.getCode());
    }

    @Test
    void isolatesConnectionLimitByTenant() {
        CommunicationEventService service = new CommunicationEventService();
        for (int index = 0; index < 5; index++) service.subscribe(7, 11, "tab-" + index);

        assertDoesNotThrow(() -> service.subscribe(8, 11, "tab-0"));
    }
}
