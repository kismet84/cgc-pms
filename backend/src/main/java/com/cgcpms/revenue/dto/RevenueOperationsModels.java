package com.cgcpms.revenue.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class RevenueOperationsModels {
    private RevenueOperationsModels() {}

    public record OwnerSettlementRequest(
            @NotNull Long projectId, @NotNull Long contractId, Long revenueId,
            @NotBlank String settlementPeriod, @NotNull LocalDate settlementDate,
            @NotNull @Positive @Digits(integer=16, fraction=2) BigDecimal grossAmount,
            @NotNull @PositiveOrZero @Digits(integer=16, fraction=2) BigDecimal taxAmount,
            @NotNull @PositiveOrZero @Digits(integer=16, fraction=2) BigDecimal retentionAmount,
            @NotNull LocalDate dueDate, @NotNull Long customerId,
            @NotNull @Min(1) Integer attachmentCount, String remark) {}

    public record AmountAllocation(@NotNull Long receivableId,
                                   @NotNull @Positive @Digits(integer=16, fraction=2) BigDecimal amount) {}

    public record SalesInvoiceRequest(
            @NotNull Long projectId, @NotNull Long contractId, @NotNull Long customerId,
            String invoiceCode, @NotBlank String invoiceNo, @NotBlank String invoiceType,
            @NotNull LocalDate invoiceDate,
            @NotNull @PositiveOrZero @Digits(integer=16, fraction=2) BigDecimal amountWithoutTax,
            @NotNull @PositiveOrZero @Digits(integer=16, fraction=2) BigDecimal taxAmount,
            @NotNull @Min(1) Integer attachmentCount,
            @NotEmpty List<@Valid AmountAllocation> allocations, String remark) {}

    public record CollectionRequest(
            @NotNull Long projectId, @NotNull Long contractId, @NotNull Long customerId,
            @NotNull Long fundAccountId, @NotBlank String externalTxnNo,
            @NotNull LocalDateTime collectedAt,
            @NotNull @Positive @Digits(integer=16, fraction=2) BigDecimal amount,
            @NotBlank String payerName, @NotNull @Min(1) Integer attachmentCount,
            List<@Valid AmountAllocation> allocations, String remark) {}

    public record CollectionReverseRequest(@NotBlank String reason, @NotBlank String idempotencyKey) {}

    public record ReceivableCreditRequest(
            @NotNull @Positive @Digits(integer=16, fraction=2) BigDecimal amount,
            @NotBlank String reason, @NotBlank String idempotencyKey) {}

    public record CollectionScheduleRequest(
            @NotNull Long projectId, @NotNull Long contractId, Long receivableId,
            @NotNull LocalDate plannedDate,
            @NotNull @Positive @Digits(integer=16, fraction=2) BigDecimal plannedAmount,
            @Min(0) @Max(365) Integer reminderDays, @NotBlank String note) {}

    public record SalesInvoiceReviewRequest(
            @NotNull Long invoiceId, @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal confidence,
            @NotNull Map<String,Object> rawResult, Map<String,Object> comparison) {}

    public record ReviewDecisionRequest(@NotBlank String decision, @NotBlank String note) {}

    public record RevenueImportRow(@NotNull Integer rowNo, @NotNull Map<String,Object> values) {}

    public record RevenueImportRequest(
            @NotBlank String importType, @NotNull Long projectId,
            @NotBlank String fileName, @NotBlank String fileHash,
            @NotEmpty List<@Valid RevenueImportRow> rows) {}

    public record RevenueIntegrationRequest(
            @NotNull Long endpointId, @NotBlank String messageType,
            @NotBlank String businessType, @NotNull Long businessId,
            @NotBlank String idempotencyKey, @NotNull Map<String,Object> payload) {}
}
