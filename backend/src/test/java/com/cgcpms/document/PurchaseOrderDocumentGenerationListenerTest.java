package com.cgcpms.document;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.event.PurchaseOrderApprovedEvent;
import com.cgcpms.document.service.DocumentGenerationService;
import com.cgcpms.document.service.PurchaseOrderDocumentGenerationListener;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PurchaseOrderDocumentGenerationListenerTest {
    @Test
    void generationFailureIsAuditedAndDoesNotEscapeAfterCommit() {
        DocumentGenerationService generationService = mock(DocumentGenerationService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        PurchaseOrderDocumentGenerationListener listener =
                new PurchaseOrderDocumentGenerationListener(generationService, events);
        PurchaseOrderApprovedEvent event = new PurchaseOrderApprovedEvent(7L, 9L, 301L, 401L);
        doThrow(new BusinessException("DOCUMENT_RENDER_FAILED", "渲染失败"))
                .when(generationService).generateSystem("PURCHASE_ORDER", 301L,
                        "PURCHASE_ORDER:301:INSTANCE:401", 7L, 9L);

        assertDoesNotThrow(() -> listener.afterCommit(event));

        verify(generationService).generateSystem("PURCHASE_ORDER", 301L,
                "PURCHASE_ORDER:301:INSTANCE:401", 7L, 9L);
        verify(events).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }
}
