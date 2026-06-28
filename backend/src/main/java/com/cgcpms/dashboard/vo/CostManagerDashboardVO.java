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
    /** Monthly trend points, sorted by month ASC, max 12. */
    private List<TrendPoint> trendPoints;
    /** Cost subject rankings by actual cost. */
    private List<SubjectRanking> subjectRankings;
    /** Over-budget alerts. */
    private List<DashboardAlertItemVO> overBudgetAlerts;
    /** Pending workflow tasks that have been waiting longer than the configured dashboard threshold. */
    private List<OverdueItem> overdueItems;
    /** Pending payment records. */
    private List<PendingPayment> pendingPayments;
    /** Cost ledger preview rows. */
    private List<LedgerRow> ledgerRows;
    /** Total rows available for ledger display. */
    private Long ledgerTotal;

    @Data
    public static class TrendPoint {
        private String month;
        private String targetCost;
        private String dynamicCost;
        private String costDeviation;
    }

    @Data
    public static class SubjectRanking {
        private String costSubjectId;
        private String costSubjectName;
        private String targetCost;
        private String actualCost;
        private String dynamicCost;
        private String costDeviation;
        private String ratio;
    }

    @Data
    public static class OverdueItem {
        private String taskId;
        private String instanceId;
        private String businessType;
        private String businessId;
        private String title;
        private Long overdueDays;
        private String ownerName;
        private String plannedAt;
        private String projectId;
        private String projectName;
    }

    @Data
    public static class PendingPayment {
        private String payRecordId;
        private String contractId;
        private String contractName;
        private String partnerName;
        private String payAmount;
        private String payDate;
        private String payStatus;
        private String projectId;
        private String projectName;
    }

    @Data
    public static class LedgerRow {
        private String rowType;
        private String costSubjectId;
        private String costSubjectName;
        private String contractCode;
        private String contractName;
        private String budgetAmount;
        private String actualAmount;
        private String completionRatio;
        private String deviationAmount;
        private String deviationRatio;
        private String status;
        private String ownerName;
    }
}
