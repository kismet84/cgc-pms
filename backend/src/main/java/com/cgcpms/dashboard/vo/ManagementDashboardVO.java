package com.cgcpms.dashboard.vo;

import lombok.Data;

import java.util.List;

/**
 * Management Dashboard View.
 * Focus: project rankings, aggregated contract totals, dynamic cost, profit, major risks.
 */
@Data
public class ManagementDashboardVO {
    /** KPI cards (tenant-wide aggregation) */
    private Long activeProjectCount;
    private String totalContractAmount;
    private String totalDynamicCost;
    private String totalExpectedProfit;
    private String totalPaidAmount;
    private Long totalPendingTaskCount;
    private Long totalRiskCount;
    /** Detail lists */
    private List<DashboardProjectSummaryVO> projectRankings;
    private List<MetricSourceVO> metricSources;
    private List<DashboardAlertItemVO> majorRisks;
    private List<DashboardTaskItemVO> overdueItems;

    @Data
    public static class MetricSourceVO {
        private String projectId;
        private String projectName;
        private String sourceType;
        private String sourceId;
        private String contractAmount;
        private String dynamicCost;
        private String expectedProfit;
        private String paidAmount;
    }
}
