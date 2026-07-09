package com.cgcpms.report.dto;

public record ReportCatalogItem(
        String code,
        String name,
        String catalog,
        String sourceType,
        String target,
        String permissionCode,
        String filterSummary,
        Boolean exportSupport,
        String status
) {
}
