package com.cgcpms.dashboard.controller;

import com.cgcpms.common.annotation.RateLimit;
import com.cgcpms.common.annotation.RateLimitKey;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.dashboard.service.DashboardService;
import com.cgcpms.dashboard.vo.*;
import com.cgcpms.tech.vo.ChiefEngineerDashboardVO;
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
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('dashboard:project-manager:view')")
    public ApiResponse<ProjectManagerDashboardVO> getProjectManagerView(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(dashboardService.getProjectManagerView(projectId));
    }

    /**
     * Business Manager View: contract totals, changes, var orders, sub-measures, payment ratio, settlement.
     */
    @GetMapping("/business-manager")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('dashboard:business-manager:view')")
    public ApiResponse<BusinessManagerDashboardVO> getBusinessManagerView(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(dashboardService.getBusinessManagerView(projectId));
    }

    /**
     * Cost Manager View: target/dynamic cost, deviations, locked cost, actual cost, expected profit.
     * Data exclusively from pre-aggregated cost_summary (never raw cost_item).
     */
    @GetMapping("/cost-manager")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('dashboard:cost-manager:view')")
    public ApiResponse<CostManagerDashboardVO> getCostManagerView(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String month) {
        return ApiResponse.success(dashboardService.getCostManagerView(projectId, month));
    }

    /**
     * Purchase Manager View: purchase requests, orders, delivery, receipts, and stock pressure.
     */
    @GetMapping("/purchase-manager")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('dashboard:purchase-manager:view')")
    public ApiResponse<PurchaseManagerDashboardVO> getPurchaseManagerView(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(dashboardService.getPurchaseManagerView(projectId));
    }

    /**
     * Production Manager MVP View: receipts, requisitions, subcontract measures, and inventory pressure.
     */
    @GetMapping("/production-manager")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('dashboard:production-manager:view')")
    public ApiResponse<ProductionManagerDashboardVO> getProductionManagerView(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(dashboardService.getProductionManagerView(projectId));
    }

    /**
     * Chief Engineer View: technical reviews, design coordination, major technical issues.
     */
    @GetMapping("/chief-engineer")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('dashboard:chief-engineer:view')")
    public ApiResponse<ChiefEngineerDashboardVO> getChiefEngineerView(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(dashboardService.getChiefEngineerView(projectId));
    }

    /**
     * Finance View: pending payments, approved unpaid, over-ratio payments, warranty expiry.
     */
    @GetMapping("/finance")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('dashboard:finance:view')")
    public ApiResponse<FinanceDashboardVO> getFinanceView(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(dashboardService.getFinanceView(projectId));
    }

    /**
     * Management View (tenant-wide): project rankings, aggregated totals, major risks.
     */
    @GetMapping("/management")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('dashboard:management:view')")
    @RateLimit(maxRequests = 60, windowSeconds = 60, key = RateLimitKey.TENANT)
    public ApiResponse<ManagementDashboardVO> getManagementView() {
        return ApiResponse.success(dashboardService.getManagementView());
    }

    /**
     * Cost Breakdown Drill-down: by cost subject (max 2 levels).
     */
    @GetMapping("/project/{id}/cost-breakdown")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('dashboard:cost-breakdown:view')")
    public ApiResponse<CostBreakdownVO> getCostBreakdown(@PathVariable("id") Long projectId) {
        return ApiResponse.success(dashboardService.getCostBreakdown(projectId));
    }
}
