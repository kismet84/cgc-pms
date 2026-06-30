package com.cgcpms.dashboard.vo;

import lombok.Data;

import java.util.List;

@Data
public class PurchaseManagerDashboardVO {
    private String projectId;
    private String projectName;
    private Long pendingRequestCount;
    private Long activeOrderCount;
    private Long overdueDeliveryCount;
    private Long pendingReceiptCount;
    private Long lowStockItemCount;
    private String totalOrderAmount;
    private List<DashboardBusinessItemVO> recentRequests;
    private List<DashboardBusinessItemVO> purchaseOrders;
    private List<DashboardBusinessItemVO> overdueOrders;
    private List<DashboardBusinessItemVO> pendingReceipts;
}
