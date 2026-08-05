package com.cgcpms.projectfile;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/project-files")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
public class ProjectFileController {
    private final ProjectFileService service;

    @GetMapping
    @PreAuthorize("hasAuthority('project:file:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PageResult<ProjectFileModels.Record>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryCode) {
        return ApiResponse.success(service.page(pageNo, pageSize, projectId, keyword, categoryCode));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('project:file:manage') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ProjectFileModels.Record> create(
            @RequestParam Long projectId,
            @RequestParam String name,
            @RequestParam String categoryCode,
            @RequestParam MultipartFile file) {
        return ApiResponse.success(service.create(projectId, name, categoryCode, file));
    }

    @PostMapping("/{catalogId}/versions")
    @PreAuthorize("hasAuthority('project:file:manage') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ProjectFileModels.Record> appendVersion(
            @PathVariable Long catalogId,
            @RequestParam MultipartFile file) {
        return ApiResponse.success(service.appendVersion(catalogId, file));
    }

    @PostMapping("/versions/{versionId}/preview")
    @PreAuthorize("hasAuthority('project:file:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    @AuditedOperation(type = "PREVIEW", businessType = "PROJECT_FILE_VERSION", businessIdExpression = "#versionId")
    public ApiResponse<ProjectFileModels.Preview> preview(@PathVariable Long versionId) {
        return ApiResponse.success(service.preview(versionId));
    }

    @GetMapping("/maintenance/import-preview")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<ProjectFileModels.ImportPreview> importPreview() {
        return ApiResponse.success(service.previewDirectProjectImport());
    }

    @PostMapping("/maintenance/import")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<ProjectFileModels.ImportResult> importDirectProjectFiles(
            @RequestParam(defaultValue = "0") long afterFileId,
            @RequestParam(defaultValue = "100") int batchSize) {
        return ApiResponse.success(service.importDirectProjectFiles(afterFileId, batchSize));
    }

    @GetMapping("/maintenance/reconcile")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<ProjectFileModels.Reconciliation> reconcile() {
        return ApiResponse.success(service.reconcile());
    }
}
