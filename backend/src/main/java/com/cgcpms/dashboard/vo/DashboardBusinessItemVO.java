package com.cgcpms.dashboard.vo;

import lombok.Data;

@Data
public class DashboardBusinessItemVO {
    private String sourceType;
    private String sourceId;
    private String code;
    private String title;
    private String itemSummary;
    private String status;
    private String amount;
    private String date;
    private String projectId;
    private String projectName;
    private String partnerName;
    private String ownerName;
    private Long overdueDays;
    private Long pendingDays;
}
