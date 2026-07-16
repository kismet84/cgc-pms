package com.cgcpms.dashboard.vo;

import lombok.Data;

import java.util.List;

/**
 * Finance Dashboard View.
 * Focus: pending payments, approved unpaid, over-ratio payments, warranty expiry.
 */
@Data
public class FinanceDashboardVO {
    private String projectId;
    private String projectName;
    /** KPI cards */
    private String pendingPaymentAmount;
    private Long pendingPaymentCount;
    private String approvedUnpaidAmount;
    private String overRatioAmount;
    private String warrantyExpiringAmount;
    private String totalContractAmount;
    private String totalPaidAmount;
    private String budgetAmount;
    private String budgetConsumedAmount;
    private String budgetExecutionRate;
    private String cashOutflowAmount;
    private String cashBalance;
    private String projectProfit;
    private String metricFormulaVersion;
    /** Detail lists */
    private List<DashboardPaymentItemVO> pendingPayments;
    private List<DashboardPaymentItemVO> overRatioPayments;
}
