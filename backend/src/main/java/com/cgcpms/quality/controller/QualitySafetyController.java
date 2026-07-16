package com.cgcpms.quality.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.quality.dto.QualitySafetyModels.*;
import com.cgcpms.quality.entity.*;
import com.cgcpms.quality.service.QualitySafetyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/quality-safety")
@RequiredArgsConstructor
public class QualitySafetyController {
    private final QualitySafetyService service;

    @GetMapping("/plans")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:query')")
    public ApiResponse<List<QualityInspectionPlan>> plans(@RequestParam Long projectId) {
        return ApiResponse.success(service.listPlans(projectId));
    }

    @PostMapping("/plans")
    @AuditedOperation(type = "CREATE", businessType = "QS_INSPECTION_PLAN")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:plan:maintain')")
    public ApiResponse<QualityInspectionPlan> createPlan(@Valid @RequestBody PlanCommand command) {
        return ApiResponse.success(service.createPlan(command));
    }

    @PutMapping("/plans/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "QS_INSPECTION_PLAN", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:plan:maintain')")
    public ApiResponse<QualityInspectionPlan> updatePlan(@PathVariable Long id, @Valid @RequestBody PlanCommand command) {
        return ApiResponse.success(service.updatePlan(id, command));
    }

    @PostMapping("/plans/{id}/activate")
    @AuditedOperation(type = "ACTIVATE", businessType = "QS_INSPECTION_PLAN", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:plan:maintain')")
    public ApiResponse<QualityInspectionPlan> activatePlan(@PathVariable Long id) {
        return ApiResponse.success(service.activatePlan(id));
    }

    @PostMapping("/plans/{id}/complete")
    @AuditedOperation(type = "COMPLETE", businessType = "QS_INSPECTION_PLAN", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:plan:maintain')")
    public ApiResponse<QualityInspectionPlan> completePlan(@PathVariable Long id) {
        return ApiResponse.success(service.completePlan(id));
    }

    @GetMapping("/inspections")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:query')")
    public ApiResponse<List<QualityInspectionRecord>> inspections(@RequestParam Long planId) {
        return ApiResponse.success(service.listInspections(planId));
    }

    @PostMapping("/inspections")
    @AuditedOperation(type = "CREATE", businessType = "QS_INSPECTION")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:inspection:maintain')")
    public ApiResponse<QualityInspectionRecord> createInspection(@Valid @RequestBody InspectionCommand command) {
        return ApiResponse.success(service.createInspection(command));
    }

    @PostMapping("/inspections/{id}/issues")
    @AuditedOperation(type = "CREATE", businessType = "QS_ISSUE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:inspection:maintain')")
    public ApiResponse<QualitySafetyIssue> createIssue(@PathVariable Long id, @Valid @RequestBody IssueCommand command) {
        return ApiResponse.success(service.createIssue(id, command));
    }

    @PostMapping("/inspections/{id}/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "QS_INSPECTION", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:inspection:maintain')")
    public ApiResponse<QualityInspectionRecord> submitInspection(@PathVariable Long id) {
        return ApiResponse.success(service.submitInspection(id));
    }

    @GetMapping("/issues")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:query')")
    public ApiResponse<List<QualitySafetyIssue>> issues(@RequestParam Long projectId,
                                                        @RequestParam(required = false) String status) {
        return ApiResponse.success(service.listIssues(projectId, status));
    }

    @PostMapping("/rectifications")
    @AuditedOperation(type = "CREATE", businessType = "QS_RECTIFICATION")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:rectify')")
    public ApiResponse<QualityRectification> createRectification(@Valid @RequestBody RectificationCommand command) {
        return ApiResponse.success(service.createRectification(command));
    }

    @PostMapping("/rectifications/{id}/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "QS_RECTIFICATION", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:rectify')")
    public ApiResponse<QualityRectification> submitRectification(@PathVariable Long id) {
        return ApiResponse.success(service.submitRectification(id));
    }

    @PostMapping("/rectifications/{id}/reinspect")
    @AuditedOperation(type = "REINSPECT", businessType = "QS_RECTIFICATION", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:reinspect')")
    public ApiResponse<QualityRectification> reinspect(@PathVariable Long id,
                                                       @Valid @RequestBody ReinspectionCommand command) {
        return ApiResponse.success(service.reinspect(id, command));
    }

    @PostMapping("/consequences")
    @AuditedOperation(type = "CREATE", businessType = "QS_CONSEQUENCE")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:consequence')")
    public ApiResponse<QualityConsequence> createConsequence(@Valid @RequestBody ConsequenceCommand command) {
        return ApiResponse.success(service.createConsequence(command));
    }

    @PostMapping("/consequences/{id}/post")
    @AuditedOperation(type = "POST", businessType = "QS_CONSEQUENCE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:consequence')")
    public ApiResponse<QualityConsequence> postConsequence(@PathVariable Long id) {
        return ApiResponse.success(service.postConsequence(id));
    }

    @GetMapping("/issues/{id}/trace")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('quality:safety:query')")
    public ApiResponse<Trace> trace(@PathVariable Long id) {
        return ApiResponse.success(service.trace(id));
    }
}
