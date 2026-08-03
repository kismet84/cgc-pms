package com.cgcpms.bid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record BidDocumentCreateRequest(
        @NotBlank String documentGroup,
        @NotBlank String documentType,
        @NotBlank @Size(max = 200) String logicalName,
        @NotNull Long sysFileId,
        @Size(max = 200) String sourceName,
        @Size(max = 1000) String sourceUrl,
        LocalDateTime publishedAt,
        LocalDateTime receivedAt,
        LocalDateTime submittedAt,
        @Size(max = 200) String externalReceiptNo) {

    public BidDocumentCreateRequest {
        documentGroup = upper(documentGroup);
        documentType = upper(documentType);
        logicalName = trim(logicalName);
        sourceName = trim(sourceName);
        sourceUrl = trim(sourceUrl);
        externalReceiptNo = trim(externalReceiptNo);
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
