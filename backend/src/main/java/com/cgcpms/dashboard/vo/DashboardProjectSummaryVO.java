package com.cgcpms.dashboard.vo;

import lombok.Data;

/**
 * Shared project-level summary card for management views and rankings.
 */
@Data
public class DashboardProjectSummaryVO {
    private String projectId;
    private String projectName;
    private String projectCode;
    private String status;
    private String targetCost;
    private String dynamicCost;
    private String contractIncome;
    private String expectedProfit;
    private String costDeviation;
    private String paidAmount;
    private String contractAmount;
    private Integer pendingTaskCount;
    private Integer riskCount;
}
