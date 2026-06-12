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
    /** Detail lists */
    private List<DashboardPaymentItemVO> pendingPayments;
    private List<DashboardPaymentItemVO> overRatioPayments;
}
