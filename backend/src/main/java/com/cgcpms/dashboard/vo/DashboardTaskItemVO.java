package com.cgcpms.dashboard.vo;

import lombok.Data;

/**
 * Shared task/approval item for dashboard views.
 */
@Data
public class DashboardTaskItemVO {
    private String taskId;
    private String instanceId;
    private String businessType;
    private String businessId;
    private String title;
    private String itemSummary;
    private String taskStatus;
    private String receivedAt;
    private String ownerName;
    private String amount;
    private Long pendingDays;
    private String projectId;
    private String projectName;
}
