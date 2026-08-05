package com.cgcpms.projectfile;

import java.util.List;

public final class ProjectFileModels {
    private ProjectFileModels() {}

    public record Version(
            String id,
            int versionNo,
            String sysFileId,
            String submitterName,
            String createdByName,
            String createdBy,
            String createdAt,
            String virusScanStatus,
            String previewStatus) {}

    public record Record(
            String id,
            String projectId,
            String projectName,
            String fileCode,
            String displayName,
            String categoryCode,
            String categoryName,
            String sourceKind,
            String maintainMode,
            String sourceHint,
            String sourceRoute,
            List<Version> versions) {}

    public record Preview(
            String status,
            String url,
            String errorCode,
            String message,
            Integer retryAfterSeconds) {}

    public record ImportException(String fileId, String businessType, String businessId, String reason) {}

    public record ImportPreview(long eligibleCount, long alreadyImportedCount, long resolvableCount,
                                long exceptionCount, List<ImportException> exceptions, String mapping) {}

    public record ImportResult(int importedCount, long lastFileId) {}

    public record Reconciliation(
            long unindexedResolvableCount,
            long catalogWithoutVersionCount,
            long duplicateSysFileReferenceGroupCount,
            long versionGapCatalogCount,
            long sourceMissingCount,
            long wrongProjectCount,
            long crossTenantReferenceCount,
            long readyPreviewMissingMetadataCount) {}
}
