package com.cgcpms.dashboard.vo;

import lombok.Data;

@Data
public class DashboardSupplierScoreVO {
    private String partnerId;
    private String partnerName;
    private Long orderCount;
    private Long overdueOrderCount;
    private String onTimeDeliveryRate;
    private String performanceScore;
}
