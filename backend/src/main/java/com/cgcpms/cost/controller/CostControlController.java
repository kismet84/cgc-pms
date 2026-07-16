package com.cgcpms.cost.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.cost.dto.CostControlModels.CorrectiveActionRequest;
import com.cgcpms.cost.dto.CostControlModels.CorrectiveCloseRequest;
import com.cgcpms.cost.dto.CostControlModels.ForecastRequest;
import com.cgcpms.cost.service.CostControlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/cost-controls")
@RequiredArgsConstructor
public class CostControlController {
    private final CostControlService service;

    @GetMapping("/projects/{projectId}/overview")
    @PreAuthorize("hasAuthority('cost:control:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> overview(@PathVariable Long projectId) {
        return ApiResponse.success(service.overview(projectId));
    }

    @GetMapping("/forecasts/{id}/trace")
    @PreAuthorize("hasAuthority('cost:control:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> trace(@PathVariable Long id) {
        return ApiResponse.success(service.trace(id));
    }

    @PostMapping("/forecasts")
    @PreAuthorize("hasAuthority('cost:forecast:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    @AuditedOperation(type = "CREATE", businessType = "COST_FORECAST")
    public ApiResponse<Map<String, Object>> createForecast(@Valid @RequestBody ForecastRequest request) {
        return ApiResponse.success(service.createForecast(request));
    }

    @PutMapping("/forecasts/{id}")
    @PreAuthorize("hasAuthority('cost:forecast:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    @AuditedOperation(type = "UPDATE", businessType = "COST_FORECAST", businessIdExpression = "#id")
    public ApiResponse<Map<String, Object>> updateForecast(@PathVariable Long id, @Valid @RequestBody ForecastRequest request) {
        return ApiResponse.success(service.updateForecast(id, request));
    }

    @PostMapping("/forecasts/{id}/confirm")
    @PreAuthorize("hasAuthority('cost:forecast:confirm') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    @AuditedOperation(type = "CONFIRM", businessType = "COST_FORECAST", businessIdExpression = "#id")
    public ApiResponse<Map<String, Object>> confirmForecast(@PathVariable Long id) {
        return ApiResponse.success(service.confirmForecast(id));
    }

    @PostMapping("/corrective-actions")
    @PreAuthorize("hasAuthority('cost:corrective:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    @AuditedOperation(type = "CREATE", businessType = "COST_CORRECTIVE_ACTION")
    public ApiResponse<Map<String, Object>> createCorrective(@Valid @RequestBody CorrectiveActionRequest request) {
        return ApiResponse.success(service.createCorrectiveAction(request));
    }

    @PutMapping("/corrective-actions/{id}")
    @PreAuthorize("hasAuthority('cost:corrective:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    @AuditedOperation(type = "UPDATE", businessType = "COST_CORRECTIVE_ACTION", businessIdExpression = "#id")
    public ApiResponse<Map<String, Object>> updateCorrective(@PathVariable Long id, @Valid @RequestBody CorrectiveActionRequest request) {
        return ApiResponse.success(service.updateCorrectiveAction(id, request));
    }

    @PostMapping("/corrective-actions/{id}/submit")
    @PreAuthorize("hasAuthority('cost:corrective:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    @AuditedOperation(type = "SUBMIT", businessType = "COST_CORRECTIVE_ACTION", businessIdExpression = "#id")
    public ApiResponse<Map<String, Object>> submitCorrective(@PathVariable Long id) {
        return ApiResponse.success(service.submitCorrectiveAction(id));
    }

    @PostMapping("/corrective-actions/{id}/close")
    @PreAuthorize("hasAuthority('cost:corrective:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    @AuditedOperation(type = "CLOSE", businessType = "COST_CORRECTIVE_ACTION", businessIdExpression = "#id")
    public ApiResponse<Map<String, Object>> closeCorrective(@PathVariable Long id, @Valid @RequestBody CorrectiveCloseRequest request) {
        return ApiResponse.success(service.closeCorrectiveAction(id, request));
    }
}
