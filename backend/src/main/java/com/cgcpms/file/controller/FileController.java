package com.cgcpms.file.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.audit.event.OperationAuditEvent;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.annotation.RateLimit;
import com.cgcpms.common.annotation.RateLimitKey;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.file.service.FileService;
import com.cgcpms.file.service.FileMaintenanceService;
import com.cgcpms.file.vo.SysFileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

import java.util.List;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
public class FileController {

    private final FileService fileService;
    private final FileMaintenanceService maintenanceService;
    private final ApplicationEventPublisher auditPublisher;
    private final HttpServletRequest request;

    @PostMapping("/upload")
    @AuditedOperation(type = "UPLOAD", businessType = "FILE", businessIdExpression = "#businessId")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(maxRequests = 20, windowSeconds = 60, key = RateLimitKey.USER)
    public ApiResponse<SysFileVO> upload(
            @RequestParam MultipartFile file,
            @RequestParam String businessType,
            @RequestParam Long businessId,
            @RequestParam(defaultValue = "OTHER") String documentType) {
        return ApiResponse.success(fileService.upload(file, businessType, businessId, documentType));
    }

    @GetMapping("/{id}/url")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<String> getUrl(@PathVariable Long id) {
        long startedAt = System.currentTimeMillis();
        try {
            FileService.PresignedFileUrl result = fileService.getPresignedFileUrl(id);
            publishDownloadAudit(result.businessType(), String.valueOf(result.businessId()), id,
                    true, null, startedAt);
            return ApiResponse.success(result.url());
        } catch (RuntimeException exception) {
            String errorCode = exception instanceof BusinessException businessException
                    ? businessException.getCode() : exception.getClass().getSimpleName();
            String businessType = "FILE";
            String businessId = null;
            try {
                var binding = fileService.findAuditBinding(id);
                if (binding.isPresent()) {
                    businessType = binding.get().businessType();
                    businessId = String.valueOf(binding.get().businessId());
                }
            } catch (RuntimeException bindingFailure) {
                log.warn("Download audit binding lookup failed: fileId={}, errorType={}",
                        id, bindingFailure.getClass().getSimpleName());
            }
            publishDownloadAudit(businessType, businessId, id, false, errorCode, startedAt);
            throw exception;
        }
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type = "DELETE", businessType = "FILE", businessIdExpression = "#id")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SysFileVO>> listByBusiness(
            @RequestParam String businessType,
            @RequestParam Long businessId) {
        return ApiResponse.success(fileService.listByBusiness(businessType, businessId));
    }

    @GetMapping("/maintenance/reconcile")
    @AuditedOperation(type = "RECONCILE", businessType = "FILE_MAINTENANCE")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<FileMaintenanceService.ReconciliationReport> reconcile() {
        return ApiResponse.success(maintenanceService.reconcile());
    }

    @PostMapping("/maintenance/rescan")
    @AuditedOperation(type = "RESCAN", businessType = "FILE_MAINTENANCE")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<FileMaintenanceService.RescanReport> rescan(
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "100") int batchSize) {
        return ApiResponse.success(maintenanceService.rescan(afterId, batchSize));
    }

    private void publishDownloadAudit(String businessType, String businessId, Long fileId,
                                      boolean success, String errorCode, long startedAt) {
        try {
            auditPublisher.publishEvent(OperationAuditEvent.builder()
                    .tenantId(UserContext.getCurrentTenantId())
                    .userId(UserContext.getCurrentUserId())
                    .operationType("DOWNLOAD")
                    .businessType(businessType)
                    .businessId(businessId)
                    .fileId(fileId)
                    .httpMethod(request.getMethod())
                    .requestPath(request.getRequestURI())
                    .successFlag(success)
                    .errorCode(errorCode)
                    .sourceIp(request.getRemoteAddr())
                    .durationMs((int) (System.currentTimeMillis() - startedAt))
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (RuntimeException auditFailure) {
            log.error("Download audit publish failed: fileId={}, errorType={}",
                    fileId, auditFailure.getClass().getSimpleName());
        }
    }
}
