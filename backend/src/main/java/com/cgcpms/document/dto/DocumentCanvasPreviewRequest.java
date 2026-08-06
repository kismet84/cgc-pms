package com.cgcpms.document.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentCanvasPreviewRequest(
        @NotBlank String businessType,
        @NotBlank String designSchema,
        Long businessId) {
}
