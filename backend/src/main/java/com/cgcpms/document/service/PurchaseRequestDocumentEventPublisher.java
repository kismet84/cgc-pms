package com.cgcpms.document.service;

import com.cgcpms.document.event.PurchaseRequestApprovedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PurchaseRequestDocumentEventPublisher {
    private final ApplicationEventPublisher events;

    public void approved(Long tenantId, Long requestedBy, Long requestId, Long instanceId) {
        events.publishEvent(new PurchaseRequestApprovedEvent(tenantId, requestedBy, requestId, instanceId));
    }
}
