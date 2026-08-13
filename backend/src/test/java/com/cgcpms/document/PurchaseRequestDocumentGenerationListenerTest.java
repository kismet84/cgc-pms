package com.cgcpms.document;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.event.PurchaseRequestApprovedEvent;
import com.cgcpms.document.service.DocumentGenerationService;
import com.cgcpms.document.service.PurchaseDocumentGenerationListener;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PurchaseRequestDocumentGenerationListenerTest {
    @Test
    void generationFailureIsAuditedAndNeverEscapesAfterCommitListener() {
        DocumentGenerationService generationService = mock(DocumentGenerationService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        PurchaseDocumentGenerationListener listener =
                new PurchaseDocumentGenerationListener(generationService, events);
        PurchaseRequestApprovedEvent event = new PurchaseRequestApprovedEvent(7L, 9L, 101L, 301L);
        doThrow(new BusinessException("DOCUMENT_TEMPLATE_DEFAULT_MISSING", "缺少默认模板"))
                .when(generationService).generateSystem("PURCHASE_REQUEST", 101L,
                        "PURCHASE_REQUEST:101:INSTANCE:301", 7L, 9L);

        assertDoesNotThrow(() -> listener.afterRequestCommit(event));

        verify(generationService).generateSystem("PURCHASE_REQUEST", 101L,
                "PURCHASE_REQUEST:101:INSTANCE:301", 7L, 9L);
        verify(events).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }
}
