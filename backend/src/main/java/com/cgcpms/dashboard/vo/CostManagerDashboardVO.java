package com.cgcpms.dashboard.vo;

import lombok.Data;

import java.util.List;

/**
 * Cost Manager Dashboard View.
 * Focus: target/dynamic cost, deviations, locked cost, actual, estimated remaining, profit.
 * Data source: pre-aggregated cost_summary (NEVER raw cost_item).
 */
@Data
public class CostManagerDashboardVO {
    private String projectId;
    private String projectName;
    /** KPI cards from cost_summary */
    private String targetCost;
    private String dynamicCost;
    private String costDeviation;
    private String contractLockedCost;
    private String actualCost;
    private String estimatedRemainingCost;
    private String expectedProfit;
    private String contractIncome;
    /** Alerts placeholder */
    private List<DashboardAlertItemVO> overBudgetAlerts;
}
