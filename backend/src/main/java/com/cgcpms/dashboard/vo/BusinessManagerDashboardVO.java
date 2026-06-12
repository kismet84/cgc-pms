package com.cgcpms.dashboard.vo;

import lombok.Data;

import java.util.List;

/**
 * Business Manager Dashboard View.
 * Focus: contract totals, changes, var orders, sub-measures, payment ratio, settlement progress.
 */
@Data
public class BusinessManagerDashboardVO {
    private String projectId;
    private String projectName;
    /** KPI cards */
    private String totalContractAmount;
    private String contractChangeAmount;
    private String varOrderAmount;
    private String subMeasureAmount;
    private String paidRatio;
    private String settlementProgress;
    /** Detail lists */
    private List<DashboardContractItemVO> recentChanges;
    private List<DashboardProjectSummaryVO> settlementItems;
}
