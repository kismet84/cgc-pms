package com.cgcpms.dashboard.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProductionManagerDashboardVO {
    private String projectId;
    private String projectName;
    private Long receiptCount;
    private Long requisitionCount;
    private Long pendingStockOutCount;
    private Long subMeasureCount;
    private Long lowStockItemCount;
    private String confirmedMeasureAmount;
    private List<DashboardBusinessItemVO> recentReceipts;
    private List<DashboardBusinessItemVO> recentRequisitions;
    private List<DashboardBusinessItemVO> recentSubMeasures;
}
