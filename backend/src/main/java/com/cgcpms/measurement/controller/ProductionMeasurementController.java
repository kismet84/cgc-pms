package com.cgcpms.measurement.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.measurement.dto.MeasurementModels.*;
import com.cgcpms.measurement.service.ProductionMeasurementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/production-measurements")
@RequiredArgsConstructor
public class ProductionMeasurementController {
    private final ProductionMeasurementService service;

    @PostMapping("/periods")
    @AuditedOperation(type="CREATE",businessType="MEASUREMENT_PERIOD",businessIdExpression="#request.projectId")
    @PreAuthorize("hasAuthority('measurement:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> createPeriod(@Valid @RequestBody PeriodRequest request) { return ApiResponse.success(service.createPeriod(request)); }

    @GetMapping("/periods")
    @PreAuthorize("hasAuthority('measurement:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> periods(@RequestParam(required=false) Long projectId,@RequestParam(required=false) Long contractId) { return ApiResponse.success(service.periods(projectId,contractId)); }

    @PostMapping("/periods/{id}/close")
    @AuditedOperation(type="CLOSE",businessType="MEASUREMENT_PERIOD",businessIdExpression="#id")
    @PreAuthorize("hasAuthority('measurement:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> closePeriod(@PathVariable Long id) { return ApiResponse.success(service.closePeriod(id)); }

    @GetMapping("/sources")
    @PreAuthorize("hasAuthority('measurement:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> sources(@RequestParam Long projectId,@RequestParam Long contractId) { return ApiResponse.success(service.sources(projectId,contractId)); }

    @PostMapping
    @AuditedOperation(type="CREATE",businessType="PRODUCTION_MEASUREMENT",businessIdExpression="#request.projectId")
    @PreAuthorize("hasAuthority('measurement:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> create(@Valid @RequestBody MeasurementRequest request) { return ApiResponse.success(service.createMeasurement(request)); }

    @GetMapping
    @PreAuthorize("hasAuthority('measurement:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> list(@RequestParam(required=false) Long projectId,@RequestParam(required=false) String status) { return ApiResponse.success(service.measurements(projectId,status)); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('measurement:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> detail(@PathVariable Long id) { return ApiResponse.success(service.measurement(id)); }

    @PostMapping("/{id}/submit")
    @AuditedOperation(type="SUBMIT",businessType="PRODUCTION_MEASUREMENT",businessIdExpression="#id")
    @PreAuthorize("hasAuthority('measurement:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> submit(@PathVariable Long id) { return ApiResponse.success(service.submitMeasurement(id)); }

    @PostMapping("/{id}/owner-submissions")
    @AuditedOperation(type="SUBMIT_OWNER",businessType="OWNER_MEASUREMENT_SUBMISSION",businessIdExpression="#id")
    @PreAuthorize("hasAuthority('measurement:owner:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> submitOwner(@PathVariable Long id,@Valid @RequestBody OwnerSubmissionRequest request) { return ApiResponse.success(service.submitToOwner(id,request)); }

    @GetMapping("/owner-submissions/list")
    @PreAuthorize("hasAuthority('measurement:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> submissions(@RequestParam(required=false) Long projectId,@RequestParam(required=false) String status) { return ApiResponse.success(service.submissions(projectId,status)); }

    @GetMapping("/owner-submissions/{id}")
    @PreAuthorize("hasAuthority('measurement:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> submission(@PathVariable Long id) { return ApiResponse.success(service.submission(id)); }

    @PostMapping("/owner-submissions/{id}/review")
    @AuditedOperation(type="OWNER_REVIEW",businessType="OWNER_MEASUREMENT_SUBMISSION",businessIdExpression="#id")
    @PreAuthorize("hasAuthority('measurement:owner:review') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> review(@PathVariable Long id,@Valid @RequestBody OwnerReviewRequest request) { return ApiResponse.success(service.review(id,request)); }

    @GetMapping("/trace/settlements/{settlementId}")
    @PreAuthorize("hasAuthority('measurement:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> trace(@PathVariable Long settlementId) { return ApiResponse.success(service.traceBySettlement(settlementId)); }
}
