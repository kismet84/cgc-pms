package com.cgcpms.supplier.dto;

import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.quality.entity.QualityPartnerEvaluation;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.supplier.entity.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class SupplierSourcingModels {
    private SupplierSourcingModels() {}

    public record EventCommand(
            @NotNull Long projectId,
            @NotNull Long purchaseRequestId,
            @Size(max = 64) String sourcingCode,
            @NotBlank @Size(max = 200) String sourcingTitle,
            @NotBlank String sourcingType,
            @NotNull LocalDateTime deadline,
            @NotBlank @Size(max = 8) String currencyCode,
            @Size(max = 500) String remark) {}

    public record InvitationCommand(@NotEmpty List<@NotNull Long> partnerIds) {}

    public record QuoteCommand(
            @NotNull Long sourcingEventId,
            @NotNull Long partnerId,
            @NotBlank @Size(max = 64) String quoteCode,
            @NotNull @DecimalMin(value = "0.01") BigDecimal totalAmount,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate,
            @NotNull @Min(0) Integer deliveryDays,
            @NotNull LocalDate validityDate,
            @NotBlank @Size(max = 2000) String commercialTerms,
            @Size(max = 500) String remark) {}

    public record DeclineCommand(@NotBlank @Size(max = 500) String reason) {}

    public record EvaluationCommand(
            @NotNull Long quoteId,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal commercialScore,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal technicalScore,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal deliveryScore,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal qualityScore,
            @NotBlank @Size(max = 1000) String evaluationComment) {}

    public record AwardCommand(@NotNull Long quoteId, @NotBlank @Size(max = 1000) String awardReason) {}
    public record LinkContractCommand(@NotNull Long contractId) {}

    public record PerformanceCommand(
            @NotNull Long purchaseOrderId,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal serviceScore,
            @NotBlank @Size(max = 1000) String evaluationComment) {}

    public record SupplierReturnCommand(
            @NotNull Long receiptId,
            @Size(max = 64) String returnCode,
            @NotNull LocalDate returnDate,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal returnQuantity,
            @NotNull @DecimalMin("0") BigDecimal returnAmount,
            @NotBlank @Size(max = 1000) String reason) {}

    public record BlacklistCommand(
            @NotNull Long performanceEvaluationId,
            @NotBlank @Size(max = 1000) String reason) {}

    public record ReviewCommand(@NotBlank String decision, @NotBlank @Size(max = 1000) String comment) {}

    public record WorkspacePage(
            PageResult<SourcingEventRow> events,
            PageResult<PerformanceRow> performance,
            PageResult<SupplierReturnRow> returns) {}

    public record SourcingEventRow(
            String id,
            String projectId,
            String purchaseRequestId,
            String sourcingCode,
            String sourcingTitle,
            String sourcingType,
            String deadline,
            String currencyCode,
            String status,
            String awardedQuoteId,
            String awardedPartnerId,
            String contractId,
            String awardReason,
            Integer version) {}

    public record PerformanceRow(
            String id,
            String projectId,
            String partnerId,
            String partnerCode,
            String partnerName,
            String contractId,
            String purchaseOrderId,
            String evaluationCode,
            String periodStart,
            String periodEnd,
            String deliveryScore,
            String qualityScore,
            String serviceScore,
            String commercialScore,
            String totalScore,
            String grade,
            String evaluationComment,
            Integer recommendBlacklist,
            String status) {}

    public record SupplierReturnRow(
            String id,
            String projectId,
            String partnerId,
            String partnerCode,
            String partnerName,
            String contractId,
            String purchaseOrderId,
            String receiptId,
            String returnCode,
            String returnDate,
            String returnQuantity,
            String returnAmount,
            String reason,
            String status) {}

    public record PerformanceCandidateRow(
            String id,
            String projectId,
            String orderCode,
            String partnerId,
            String partnerCode,
            String partnerName) {}

    public record SourcingTrace(
            SourcingEvent event,
            MatPurchaseRequest purchaseRequest,
            List<SourcingSupplier> invitedSuppliers,
            List<SupplierQuote> quotes,
            List<BidEvaluation> bidEvaluations,
            CtContract contract,
            List<MatPurchaseOrder> purchaseOrders,
            List<MatReceipt> receipts,
            List<SupplierReturn> supplierReturns,
            List<StlSettlement> settlements,
            List<SupplierPerformanceEvaluation> performanceEvaluations,
            List<SupplierBlacklistRecord> blacklistRecords,
            List<QualityPartnerEvaluation> qualitySafetyFacts) {}
}
