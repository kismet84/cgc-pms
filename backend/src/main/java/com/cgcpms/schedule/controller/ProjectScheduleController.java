package com.cgcpms.schedule.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.schedule.dto.ProjectScheduleModels.*;
import com.cgcpms.schedule.service.ProjectScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/project-schedules")
@RequiredArgsConstructor
public class ProjectScheduleController {
    private final ProjectScheduleService service;

    @PostMapping
    @AuditedOperation(type="CREATE", businessType="PROJECT_SCHEDULE", businessIdExpression="#request.projectId")
    @PreAuthorize("hasAuthority('schedule:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> create(@Valid @RequestBody ScheduleRequest request) {
        return ApiResponse.success(service.createSchedule(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('schedule:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> list(@RequestParam(required=false) Long projectId) {
        return ApiResponse.success(service.schedules(projectId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> detail(@PathVariable Long id) {
        return ApiResponse.success(service.schedule(id));
    }

    @PutMapping("/{id}/tasks")
    @AuditedOperation(type="REPLACE_WBS", businessType="PROJECT_SCHEDULE", businessIdExpression="#id")
    @PreAuthorize("hasAuthority('schedule:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> replaceTasks(@PathVariable Long id, @Valid @RequestBody WbsTaskBatch batch) {
        return ApiResponse.success(service.replaceTasks(id, batch));
    }

    @PostMapping("/{id}/submit")
    @AuditedOperation(type="SUBMIT", businessType="PROJECT_SCHEDULE", businessIdExpression="#id")
    @PreAuthorize("hasAuthority('schedule:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> submit(@PathVariable Long id) {
        return ApiResponse.success(service.submitSchedule(id));
    }

    @PostMapping("/{id}/period-plans")
    @AuditedOperation(type="CREATE_PERIOD_PLAN", businessType="PROJECT_PERIOD_PLAN", businessIdExpression="#id")
    @PreAuthorize("hasAuthority('schedule:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> createPeriod(@PathVariable Long id, @Valid @RequestBody PeriodPlanRequest request) {
        if (!id.equals(request.schedulePlanId())) throw new IllegalArgumentException("路径计划ID与请求计划ID不一致");
        return ApiResponse.success(service.createPeriodPlan(request));
    }

    @GetMapping("/period-plans/{id}")
    @PreAuthorize("hasAuthority('schedule:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> periodDetail(@PathVariable Long id) {
        return ApiResponse.success(service.periodPlan(id));
    }

    @PutMapping("/period-plans/{id}/items")
    @AuditedOperation(type="REPLACE_ITEMS", businessType="PROJECT_PERIOD_PLAN", businessIdExpression="#id")
    @PreAuthorize("hasAuthority('schedule:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> replacePeriodItems(@PathVariable Long id, @Valid @RequestBody PeriodItemBatch batch) {
        return ApiResponse.success(service.replacePeriodItems(id, batch));
    }

    @PostMapping("/period-plans/{id}/submit")
    @AuditedOperation(type="SUBMIT", businessType="PROJECT_PERIOD_PLAN", businessIdExpression="#id")
    @PreAuthorize("hasAuthority('schedule:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> submitPeriod(@PathVariable Long id) {
        return ApiResponse.success(service.submitPeriodPlan(id));
    }

    @GetMapping("/daily-logs/{dailyLogId}/progress")
    @PreAuthorize("hasAuthority('schedule:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> dailyProgress(@PathVariable Long dailyLogId) {
        return ApiResponse.success(service.dailyProgress(dailyLogId));
    }

    @PutMapping("/daily-logs/{dailyLogId}/progress")
    @AuditedOperation(type="REPORT_PROGRESS", businessType="SITE_DAILY_LOG", businessIdExpression="#dailyLogId")
    @PreAuthorize("hasAuthority('schedule:progress') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> replaceDailyProgress(@PathVariable Long dailyLogId, @Valid @RequestBody DailyProgressBatch batch) {
        return ApiResponse.success(service.replaceDailyProgress(dailyLogId, batch));
    }

    @PostMapping("/{id}/snapshots")
    @AuditedOperation(type="CALCULATE_DEVIATION", businessType="PROJECT_SCHEDULE", businessIdExpression="#id")
    @PreAuthorize("hasAuthority('schedule:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> snapshot(@PathVariable Long id,
            @RequestParam @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate date) {
        return ApiResponse.success(service.calculateSnapshot(id, date));
    }

    @PostMapping("/{id}/corrective-actions")
    @AuditedOperation(type="CREATE_CORRECTIVE", businessType="PROJECT_CORRECTIVE_ACTION", businessIdExpression="#id")
    @PreAuthorize("hasAuthority('schedule:correct') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> createCorrective(@PathVariable Long id, @Valid @RequestBody CorrectiveActionRequest request) {
        return ApiResponse.success(service.createCorrectiveAction(id, request));
    }

    @PostMapping("/corrective-actions/{id}/submit")
    @AuditedOperation(type="SUBMIT", businessType="PROJECT_CORRECTIVE_ACTION", businessIdExpression="#id")
    @PreAuthorize("hasAuthority('schedule:correct') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> submitCorrective(@PathVariable Long id) {
        return ApiResponse.success(service.submitCorrectiveAction(id));
    }

    @GetMapping("/{id}/trace")
    @PreAuthorize("hasAuthority('schedule:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> trace(@PathVariable Long id) {
        return ApiResponse.success(service.trace(id));
    }
}
