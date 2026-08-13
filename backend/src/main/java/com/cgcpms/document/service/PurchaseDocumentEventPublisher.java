package com.cgcpms.document.service;

import com.cgcpms.document.event.PurchaseOrderApprovedEvent;
import com.cgcpms.document.event.PurchaseRequestApprovedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PurchaseDocumentEventPublisher {
    private final ApplicationEventPublisher events;

    public void requestApproved(Long tenantId, Long requestedBy, Long requestId, Long instanceId) {
        events.publishEvent(new PurchaseRequestApprovedEvent(tenantId, requestedBy, requestId, instanceId));
    }

    public void orderApproved(Long tenantId, Long requestedBy, Long orderId, Long instanceId) {
        events.publishEvent(new PurchaseOrderApprovedEvent(tenantId, requestedBy, orderId, instanceId));
    }
}
