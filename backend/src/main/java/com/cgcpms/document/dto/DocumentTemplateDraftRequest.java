package com.cgcpms.document.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentTemplateDraftRequest(
        @NotBlank String schemaVersion,
        @NotBlank String templateContent,
        @NotBlank String fieldManifest,
        String remark) {
}
