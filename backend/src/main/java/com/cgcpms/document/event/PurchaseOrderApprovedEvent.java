package com.cgcpms.document.event;

public record PurchaseOrderApprovedEvent(
        Long tenantId,
        Long requestedBy,
        Long orderId,
        Long instanceId
) {}
