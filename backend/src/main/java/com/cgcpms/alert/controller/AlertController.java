package com.cgcpms.alert.controller;

import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.service.AlertEvaluationService;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertEvaluationService alertEvaluationService;

    /**
     * List alerts for the current tenant, optionally filtered by project / severity / read status.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('alert:view') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<AlertLog>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Integer isRead) {
        Long tenantId = UserContext.getCurrentTenantId();
        return ApiResponse.success(alertEvaluationService.list(tenantId, projectId, severity, isRead));
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
