package com.cgcpms.revenue.vo;

/**
 * 收入与回款稳定视图。ID、金额和时间统一使用字符串，避免前端精度与字段命名漂移。
 */
public final class RevenueOperationsVOs {
    private RevenueOperationsVOs() {}

    public record OwnerSettlementVO(
            String id, String projectId, String contractId, String revenueId, String customerId,
            String settlementCode, String settlementPeriod, String settlementDate,
            String grossAmount, String taxAmount, String retentionAmount, String netReceivableAmount,
            String dueDate, String status, Integer attachmentCount, String approvalInstanceId,
            String formulaVersion, String version, String remark) {}

    public record ReceivableVO(
            String id, String projectId, String contractId, String settlementId, String customerId,
            String receivableCode, String receivableType, String originalAmount, String collectedAmount,
            String creditedAmount, String outstandingAmount, String dueDate, String status,
            boolean overdue, String version) {}

    public record SalesInvoiceVO(
            String id, String projectId, String contractId, String customerId, String invoiceCode,
            String invoiceNo, String invoiceType, String invoiceDate, String amountWithoutTax,
            String taxAmount, String totalAmount, String allocatedAmount, String status,
            String verificationStatus, Integer attachmentCount, String version, String remark) {}

    public record CollectionVO(
            String id, String projectId, String contractId, String customerId, String fundAccountId,
            String collectionCode, String externalTxnNo, String collectedAt, String amount,
            String allocatedAmount, String unallocatedAmount, String payerName, String status,
            Integer attachmentCount, String version, String remark) {}

    public record RevenueDashboardVO(
            String projectId, String confirmedRevenue, String settledAmount, String receivableAmount,
            String outstandingAmount, String collectedAmount, String overdueAmount,
            String invoicedAmount, String collectionRate) {}

    public record ReceivableAdjustmentVO(
            String id, String receivableId, String adjustmentType, String amount, String reason,
            String idempotencyKey, String status) {}

    public record CollectionReversalVO(
            String id, String collectionId, String idempotencyKey, String reason, String status) {}
}
