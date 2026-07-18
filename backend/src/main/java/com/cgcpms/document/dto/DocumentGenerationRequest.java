package com.cgcpms.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentGenerationRequest(
        @NotBlank String businessType,
        @NotNull Long businessId,
        @NotBlank String idempotencyKey,
        Long retryOfGenerationId) {
}
