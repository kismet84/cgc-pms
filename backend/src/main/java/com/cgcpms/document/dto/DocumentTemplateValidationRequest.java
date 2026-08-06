package com.cgcpms.document.dto;

import jakarta.validation.constraints.NotBlank;

/** 未持久化草稿的字段/语法校验请求。 */
public record DocumentTemplateValidationRequest(
        @NotBlank String businessType,
        @NotBlank String schemaVersion,
        String templateContent,
        String fieldManifest,
        String remark,
        String designSchema) {

    public DocumentTemplateValidationRequest(String businessType, String schemaVersion, String templateContent,
                                             String fieldManifest, String remark) {
        this(businessType, schemaVersion, templateContent, fieldManifest, remark, null);
    }
}
