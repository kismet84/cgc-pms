package com.cgcpms.purchase.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PurchaseRequestApprovalItemCommand(
        @NotNull Long itemId,
        @NotNull @Positive BigDecimal approvedQuantity,
        @NotNull Integer approvalVersion,
        String changeReason) {
}
