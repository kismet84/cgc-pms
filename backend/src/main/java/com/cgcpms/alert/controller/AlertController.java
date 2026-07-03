package com.cgcpms.alert.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.service.AlertEvaluationService;
import com.cgcpms.alert.service.AlertSubscriptionService;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertEvaluationService alertEvaluationService;
    private final AlertSubscriptionService alertSubscriptionService;

    /**
     * List alerts for the current tenant with backend pagination and filters.
     */
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

    /**
     * Mark a single alert as read.
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("hasAuthority('alert:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> markRead(@PathVariable Long id) {
        Long tenantId = UserContext.getCurrentTenantId();
        boolean ok = alertEvaluationService.markRead(tenantId, id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("alertId", id);
        return ApiResponse.success(result);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('alert:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                         @RequestBody Map<String, String> request) {
        Long tenantId = UserContext.getCurrentTenantId();
        String processStatus = request.get("processStatus");
        String statusRemark = request.get("statusRemark");
        boolean ok = alertEvaluationService.updateStatus(tenantId, id, processStatus, statusRemark);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("alertId", id);
        result.put("processStatus", processStatus);
        return ApiResponse.success(result);
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
    @PreAuthorize("hasAuthority('alert:view') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> updateSubscription(@RequestBody Map<String, Object> request) {
        Long tenantId = UserContext.getCurrentTenantId();
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(alertSubscriptionService.updateCurrentUserSubscription(
                tenantId, userId, UserContext.getCurrentRoles(), request));
    }

    /**
     * Manually trigger batch evaluation for all active projects in the current tenant.
     */
    @PostMapping("/batch-evaluate")
    @PreAuthorize("hasAuthority('alert:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> batchEvaluate() {
        Long tenantId = UserContext.getCurrentTenantId();
        int count = alertEvaluationService.batchEvaluate(tenantId);
        Map<String, Object> result = new HashMap<>();
        result.put("alertsGenerated", count);
        result.put("tenantId", tenantId);
        return ApiResponse.success(result);
    }
}
