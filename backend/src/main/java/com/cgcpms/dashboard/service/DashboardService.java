package com.cgcpms.dashboard.service;

import com.cgcpms.dashboard.vo.BusinessManagerDashboardVO;
import com.cgcpms.dashboard.vo.CostBreakdownVO;
import com.cgcpms.dashboard.vo.CostManagerDashboardVO;
import com.cgcpms.dashboard.vo.FinanceDashboardVO;
import com.cgcpms.dashboard.vo.ManagementDashboardVO;
import com.cgcpms.dashboard.vo.ProductionManagerDashboardVO;
import com.cgcpms.dashboard.vo.ProjectManagerDashboardVO;
import com.cgcpms.dashboard.vo.PurchaseManagerDashboardVO;
import com.cgcpms.tech.vo.ChiefEngineerDashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardProjectBusinessService projectBusinessService;
    private final DashboardCostService costService;
    private final DashboardMaterialRoleService materialRoleService;
    private final DashboardFinanceManagementService financeManagementService;

    public ProjectManagerDashboardVO getProjectManagerView(Long projectId) {
        return getProjectManagerView(projectId, null);
    }

    public ProjectManagerDashboardVO getProjectManagerView(Long projectId, String month) {
        return projectBusinessService.getProjectManagerView(projectId, month);
    }

    public BusinessManagerDashboardVO getBusinessManagerView(Long projectId) {
        return getBusinessManagerView(projectId, null);
    }

    public BusinessManagerDashboardVO getBusinessManagerView(Long projectId, String month) {
        return projectBusinessService.getBusinessManagerView(projectId, month);
    }

    public CostManagerDashboardVO getCostManagerView(Long projectId) {
        return getCostManagerView(projectId, null);
    }

    public CostManagerDashboardVO getCostManagerView(Long projectId, String month) {
        return costService.getCostManagerView(projectId, month);
    }

    public PurchaseManagerDashboardVO getPurchaseManagerView(Long projectId) {
        return getPurchaseManagerView(projectId, null);
    }

    public PurchaseManagerDashboardVO getPurchaseManagerView(Long projectId, String month) {
        return materialRoleService.getPurchaseManagerView(projectId, month);
    }

    public ProductionManagerDashboardVO getProductionManagerView(Long projectId) {
        return getProductionManagerView(projectId, null);
    }

    public ProductionManagerDashboardVO getProductionManagerView(Long projectId, String month) {
        return materialRoleService.getProductionManagerView(projectId, month);
    }

    public ChiefEngineerDashboardVO getChiefEngineerView(Long projectId) {
        return getChiefEngineerView(projectId, null);
    }

    public ChiefEngineerDashboardVO getChiefEngineerView(Long projectId, String month) {
        return materialRoleService.getChiefEngineerView(projectId, month);
    }

    public FinanceDashboardVO getFinanceView(Long projectId) {
        return getFinanceView(projectId, null);
    }

    public FinanceDashboardVO getFinanceView(Long projectId, String month) {
        return financeManagementService.getFinanceView(projectId, month);
    }

    public ManagementDashboardVO getManagementView() {
        return getManagementView(null);
    }

    public ManagementDashboardVO getManagementView(Long projectId) {
        return getManagementView(projectId, null);
    }

    public ManagementDashboardVO getManagementView(Long projectId, String month) {
        return financeManagementService.getManagementView(projectId, month);
    }

    public CostBreakdownVO getCostBreakdown(Long projectId) {
        return getCostBreakdown(projectId, null);
    }

    public CostBreakdownVO getCostBreakdown(Long projectId, String month) {
        return costService.getCostBreakdown(projectId, month);
    }
}
