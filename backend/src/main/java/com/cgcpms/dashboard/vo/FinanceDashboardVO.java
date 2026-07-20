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
    /** Monthly payment trend points, sorted by month ASC, max 12. */
    private List<TrendPoint> trendPoints;
    /** Detail lists */
    private List<DashboardPaymentItemVO> pendingPayments;
    private List<DashboardPaymentItemVO> overRatioPayments;
    private List<ContractFundBreakdown> contractFundBreakdowns;

    @Data
    public static class TrendPoint {
        private String month;
        private String cashOutflowAmount;
        private String cumulativePaidAmount;
        private String pendingPaymentAmount;
    }

    @Data
    public static class ContractFundBreakdown {
        private String contractId;
        private String projectId;
        private String projectName;
        private String contractCode;
        private String contractName;
        private String contractAmount;
        private String paidAmount;
        private String approvingAmount;
        private String approvedUnpaidAmount;
        private String remainingAmount;
        private String paymentRatio;
        private List<DashboardPaymentItemVO> paymentRecords;
    }
}
