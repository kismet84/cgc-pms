package com.cgcpms.document.event;

public record PurchaseRequestApprovedEvent(
        Long tenantId,
        Long requestedBy,
        Long requestId,
        Long instanceId
) {}
