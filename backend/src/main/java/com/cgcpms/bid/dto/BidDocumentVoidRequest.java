package com.cgcpms.bid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BidDocumentVoidRequest(
        @NotBlank(message = "作废原因不能为空")
        @Size(max = 500, message = "作废原因不能超过500字") String reason) {

    public BidDocumentVoidRequest {
        reason = reason == null ? null : reason.trim();
    }
}
