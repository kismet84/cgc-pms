package com.cgcpms.document.dto;

import jakarta.validation.constraints.NotBlank;

/** 未持久化草稿的字段/语法校验请求。 */
public record DocumentTemplateValidationRequest(
        @NotBlank String businessType,
        @NotBlank String schemaVersion,
        @NotBlank String templateContent,
        @NotBlank String fieldManifest,
        String remark) {
}
