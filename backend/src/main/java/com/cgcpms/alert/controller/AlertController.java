package com.cgcpms.alert.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.alert.dto.AlertBatchReadRequest;
import com.cgcpms.alert.dto.AlertBatchStatusUpdateRequest;
import com.cgcpms.alert.dto.AlertExportAuditRequest;
import com.cgcpms.alert.dto.AlertOperationResponse;
import com.cgcpms.alert.dto.AlertProcessingReportVO;
import com.cgcpms.alert.dto.AlertStatusUpdateRequest;
import com.cgcpms.alert.dto.AlertSubscriptionUpdateRequest;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.service.AlertEvaluationService;
import com.cgcpms.alert.service.AlertSubscriptionService;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertEvaluationService alertEvaluationService;
    private final AlertSubscriptionService alertSubscriptionService;

    @GetMapping
    @PreAuthorize("hasAuthority('alert:view') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PageResult<AlertLog>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String alertDomain,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(required = false) String processStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime triggeredStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime triggeredEnd) {
        Long tenantId = UserContext.getCurrentTenantId();
        IPage<AlertLog> page = alertEvaluationService.page(tenantId, pageNum, pageSize, projectId,
                ruleType, alertDomain, severity, isRead, triggeredStart, triggeredEnd, processStatus);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/processing-report")
    @PreAuthorize("hasAuthority('alert:view') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<AlertProcessingReportVO> processingReport(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String alertDomain,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(required = false) String processStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime triggeredStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime triggeredEnd) {
        Long tenantId = UserContext.getCurrentTenantId();
        return ApiResponse.success(alertEvaluationService.processingReport(tenantId, projectId,
                ruleType, alertDomain, severity, isRead, triggeredStart, triggeredEnd, processStatus));
    }

    @PostMapping("/export-audit")
    @AuditedOperation(type = "DOWNLOAD", businessType = "ALERT_EXPORT", businessIdExpression = "#request.filterSignature")
    @PreAuthorize("hasAuthority('alert:view') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> exportAudit(@Valid @RequestBody AlertExportAuditRequest request) {
        return ApiResponse.success();
    }

    @PutMapping("/{id}/read")
    @AuditedOperation(type = "UPDATE", businessType = "ALERT")
    @PreAuthorize("hasAuthority('alert:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<AlertOperationResponse> markRead(@PathVariable Long id) {
        Long tenantId = UserContext.getCurrentTenantId();
        boolean ok = alertEvaluationService.markRead(tenantId, id);
        return ApiResponse.success(new AlertOperationResponse(ok, id));
    }

    @PutMapping("/batch/read")
    @AuditedOperation(type = "UPDATE", businessType = "ALERT")
    @PreAuthorize("hasAuthority('alert:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> batchMarkRead(
            @Valid @RequestBody AlertBatchReadRequest request) {
        Long tenantId = UserContext.getCurrentTenantId();
        return ApiResponse.success(alertEvaluationService.batchMarkRead(tenantId, request.getAlertIds()));
    }

    @PutMapping("/{id}/status")
    @AuditedOperation(type = "UPDATE", businessType = "ALERT")
    @PreAuthorize("hasAuthority('alert:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<AlertOperationResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AlertStatusUpdateRequest request) {
        Long tenantId = UserContext.getCurrentTenantId();
        boolean ok = alertEvaluationService.updateStatus(tenantId, id,
                request.getProcessStatus(), request.getStatusRemark());
        return ApiResponse.success(new AlertOperationResponse(ok, id, request.getProcessStatus()));
    }

    @PutMapping("/batch/status")
    @AuditedOperation(type = "UPDATE", businessType = "ALERT")
    @PreAuthorize("hasAuthority('alert:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> batchUpdateStatus(
            @Valid @RequestBody AlertBatchStatusUpdateRequest request) {
        Long tenantId = UserContext.getCurrentTenantId();
        return ApiResponse.success(alertEvaluationService.batchUpdateStatus(
                tenantId, request.getAlertIds(),
                request.getProcessStatus(), request.getStatusRemark()));
    }

    @GetMapping("/subscription")
    @PreAuthorize("hasAuthority('alert:view') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> getSubscription() {
        Long tenantId = UserContext.getCurrentTenantId();
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(alertSubscriptionService.getCurrentUserSubscription(
                tenantId, userId, UserContext.getCurrentRoles()));
    }

    @PutMapping("/subscription")
    @AuditedOperation(type = "UPDATE", businessType = "ALERT")
    @PreAuthorize("hasAuthority('alert:view') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> updateSubscription(
            @RequestBody AlertSubscriptionUpdateRequest request) {
        Long tenantId = UserContext.getCurrentTenantId();
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(alertSubscriptionService.updateCurrentUserSubscription(
                tenantId, userId, UserContext.getCurrentRoles(), request.getSubscription()));
    }

    @PostMapping("/batch-evaluate")
    @AuditedOperation(type = "CREATE", businessType = "ALERT")
    @PreAuthorize("hasAuthority('alert:evaluate') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> batchEvaluate() {
        Long tenantId = UserContext.getCurrentTenantId();
        int count = alertEvaluationService.batchEvaluate(tenantId);
        return ApiResponse.success(Map.of("alertsGenerated", count, "tenantId", tenantId));
    }
}
