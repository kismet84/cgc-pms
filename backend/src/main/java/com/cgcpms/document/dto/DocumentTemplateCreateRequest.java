package com.cgcpms.document.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentTemplateCreateRequest(
        String templateCode,
        @NotBlank String templateName,
        @NotBlank String businessType,
        @NotBlank String schemaVersion,
        String templateContent,
        String fieldManifest,
        String remark,
        String designSchema) {

    public DocumentTemplateCreateRequest(String templateCode, String templateName, String businessType,
                                         String schemaVersion, String templateContent, String fieldManifest,
                                         String remark) {
        this(templateCode, templateName, businessType, schemaVersion, templateContent, fieldManifest, remark, null);
    }
}
