package com.cgcpms.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentPreviewRequest(@NotBlank String businessType, @NotNull Long businessId) {
}
