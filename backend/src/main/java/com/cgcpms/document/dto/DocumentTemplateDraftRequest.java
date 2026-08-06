package com.cgcpms.document.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentTemplateDraftRequest(
        @NotBlank String schemaVersion,
        String templateContent,
        String fieldManifest,
        String remark,
        String designSchema) {

    public DocumentTemplateDraftRequest(String schemaVersion, String templateContent, String fieldManifest,
                                        String remark) {
        this(schemaVersion, templateContent, fieldManifest, remark, null);
    }
}
