package com.cgcpms.document.service;

import com.cgcpms.document.event.PurchaseOrderApprovedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PurchaseOrderDocumentEventPublisher {
    private final ApplicationEventPublisher events;

    public void approved(Long tenantId, Long requestedBy, Long orderId, Long instanceId) {
        events.publishEvent(new PurchaseOrderApprovedEvent(tenantId, requestedBy, orderId, instanceId));
    }
}
