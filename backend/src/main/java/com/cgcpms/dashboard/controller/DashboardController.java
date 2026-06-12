package com.cgcpms.dashboard.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.dashboard.service.DashboardService;
import com.cgcpms.dashboard.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Project Manager View: pending tasks, lagging projects, expiring contracts, pending approvals.
     */
    @GetMapping("/project-manager")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('dashboard:project-manager:view')")
    public ApiResponse<ProjectManagerDashboardVO> getProjectManagerView(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(dashboardService.getProjectManagerView(projectId));
    }

    /**
     * Business Manager View: contract totals, changes, var orders, sub-measures, payment ratio, settlement.
     */
    @GetMapping("/business-manager")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('dashboard:business-manager:view')")
    public ApiResponse<BusinessManagerDashboardVO> getBusinessManagerView(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(dashboardService.getBusinessManagerView(projectId));
    }

    /**
     * Cost Manager View: target/dynamic cost, deviations, locked cost, actual cost, expected profit.
     * Data exclusively from pre-aggregated cost_summary (never raw cost_item).
     */
    @GetMapping("/cost-manager")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('dashboard:cost-manager:view')")
    public ApiResponse<CostManagerDashboardVO> getCostManagerView(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(dashboardService.getCostManagerView(projectId));
    }

    /**
     * Finance View: pending payments, approved unpaid, over-ratio payments, warranty expiry.
     */
    @GetMapping("/finance")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('dashboard:finance:view')")
    public ApiResponse<FinanceDashboardVO> getFinanceView(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(dashboardService.getFinanceView(projectId));
    }

    /**
     * Management View (tenant-wide): project rankings, aggregated totals, major risks.
     */
    @GetMapping("/management")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('dashboard:management:view')")
    public ApiResponse<ManagementDashboardVO> getManagementView() {
        return ApiResponse.success(dashboardService.getManagementView());
    }

    /**
     * Cost Breakdown Drill-down: by cost subject (max 2 levels).
     */
    @GetMapping("/project/{id}/cost-breakdown")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('dashboard:cost-breakdown:view')")
    public ApiResponse<CostBreakdownVO> getCostBreakdown(@PathVariable("id") Long projectId) {
        return ApiResponse.success(dashboardService.getCostBreakdown(projectId));
    }
}
