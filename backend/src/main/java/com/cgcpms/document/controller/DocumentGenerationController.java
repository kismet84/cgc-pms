package com.cgcpms.document.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.annotation.RateLimit;
import com.cgcpms.common.annotation.RateLimitKey;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.document.dto.DocumentGenerationRequest;
import com.cgcpms.document.dto.DocumentPreviewRequest;
import com.cgcpms.document.entity.DocumentGeneration;
import com.cgcpms.document.service.DocumentGenerationService;
import com.cgcpms.document.service.DocumentGenerationReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/documents/generations")
@RequiredArgsConstructor
public class DocumentGenerationController {
    private final DocumentGenerationService service;
    private final DocumentGenerationReconciliationService reconciliationService;

    @PostMapping
    @AuditedOperation(type = "GENERATE", businessType = "DOCUMENT", businessIdExpression = "#request.businessId")
    @PreAuthorize("hasAuthority('document:generate') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    @RateLimit(maxRequests = 10, windowSeconds = 60, key = RateLimitKey.USER)
    public ApiResponse<DocumentGeneration> generate(@Valid @RequestBody DocumentGenerationRequest request) {
        return ApiResponse.success(service.generate(request.businessType(), request.businessId(),
                request.idempotencyKey(), request.retryOfGenerationId()));
    }

    @PostMapping(value = "/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    @AuditedOperation(type = "PREVIEW", businessType = "DOCUMENT", businessIdExpression = "#request.businessId")
    @PreAuthorize("hasAuthority('document:generate') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    @RateLimit(maxRequests = 10, windowSeconds = 60, key = RateLimitKey.USER)
    public ResponseEntity<byte[]> preview(@Valid @RequestBody DocumentPreviewRequest request) {
        byte[] content = service.preview(request.businessType(), request.businessId()).content();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=preview.pdf")
                .body(content);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('document:history:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PageResult<DocumentGeneration>> history(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam String businessType,
            @RequestParam Long businessId) {
        IPage<DocumentGeneration> page = service.history(pageNo, pageSize, businessType, businessId);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('document:history:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<DocumentGeneration> get(@PathVariable Long id) {
        return ApiResponse.success(service.requireGeneration(id));
    }

    @GetMapping("/{id}/download")
    @AuditedOperation(type = "DOWNLOAD", businessType = "DOCUMENT", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('document:download') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<String> download(@PathVariable Long id) {
        return ApiResponse.success(service.downloadUrl(id));
    }

    @GetMapping("/{id}/audit-download")
    @AuditedOperation(type = "AUDIT_DOWNLOAD", businessType = "DOCUMENT", businessIdExpression = "#id")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('document:audit:download')")
    public ApiResponse<String> auditDownload(@PathVariable Long id,
                                             @RequestParam String reason) {
        return ApiResponse.success(service.auditDownloadUrl(id, reason));
    }

    @PostMapping("/reconcile")
    @AuditedOperation(type = "RECONCILE", businessType = "DOCUMENT", businessIdExpression = "0")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<DocumentGenerationReconciliationService.ReconciliationResult> reconcile(
            @RequestParam(defaultValue = "30") int staleMinutes) {
        return ApiResponse.success(reconciliationService.reconcileCurrentTenant(staleMinutes));
    }
}
