package com.cgcpms.bid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BidStatusUpdateRequest(
        @NotBlank(message = "目标状态不能为空") String targetStatus,
        @NotBlank(message = "预期状态不能为空") String expectedStatus,
        @Size(max = 500, message = "原因不能超过500字") String reason) {

    public BidStatusUpdateRequest {
        targetStatus = normalize(targetStatus);
        expectedStatus = normalize(expectedStatus);
        reason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase();
        return "BIDDING".equals(normalized) ? "PREPARING" : normalized;
    }
}
