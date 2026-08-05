package com.cgcpms.communication;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.communication.service.CommunicationEventService;
import org.junit.jupiter.api.Test;

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
}
