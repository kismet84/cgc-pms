package com.cgcpms.financeops.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class FinanceOperationsModels {
    private FinanceOperationsModels() {}

    public record BudgetAdjustmentRequest(@NotNull Long budgetLineId,
                                          @NotNull @Digits(integer=16, fraction=2) BigDecimal deltaAmount,
                                          @NotBlank String reason, @NotBlank String idempotencyKey) {}
    public record BudgetTransferRequest(@NotNull Long fromBudgetLineId, @NotNull Long toBudgetLineId,
                                        @NotNull @Positive @Digits(integer=16, fraction=2) BigDecimal amount,
                                        @NotBlank String reason, @NotBlank String idempotencyKey) {}
    public record ContractQuotaReleaseRequest(@NotNull Long contractAllocationId,
                                              @NotNull @Positive @Digits(integer=16, fraction=2) BigDecimal amount,
                                              @NotBlank String reason, @NotBlank String idempotencyKey) {}
    public record PaymentScheduleRequest(@NotNull Long projectId, @NotNull Long contractId,
                                         Long payApplicationId, @NotBlank String scheduleName,
                                         @NotNull LocalDate plannedDate,
                                         @NotNull @Positive @Digits(integer=16, fraction=2) BigDecimal plannedAmount,
                                         @Min(0) @Max(365) Integer reminderDays) {}
    public record AlertHandleRequest(@NotBlank String status, @NotBlank String note) {}
    public record InvoiceExceptionRequest(@NotBlank String status, @NotBlank String reason) {}
    public record OcrReviewCreateRequest(@NotNull Long invoiceId,
                                         @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal confidence,
                                         @NotNull Map<String,Object> rawResult,
                                         Map<String,Object> comparison) {}
    public record OcrReviewDecisionRequest(@NotBlank String decision, @NotBlank String note) {}
    public record ImportRow(@NotNull Integer rowNo, @NotNull Map<String,Object> values) {}
    public record ImportPreviewRequest(@NotBlank String importType, @NotNull Long projectId,
                                       @NotBlank String fileName, @NotBlank String fileHash,
                                       @NotEmpty List<@Valid ImportRow> rows) {}
    public record RoutingRuleRequest(@NotBlank String ruleName, @NotBlank String businessType,
                                     BigDecimal minAmount, BigDecimal maxAmount, String contractType,
                                     String expenseCategory, @NotNull Long workflowTemplateId,
                                     Integer priority, Boolean enabled) {}
    public record RoutingMatchRequest(@NotBlank String businessType, @NotNull BigDecimal amount,
                                      String contractType, String expenseCategory) {}
    public record IntegrationEndpointRequest(@NotBlank String endpointType, @NotBlank String endpointCode,
                                             @NotBlank String endpointName, String baseUrl,
                                             String credentialRef, String callbackSecret,
                                             Map<String,Object> config) {}
    public record IntegrationMessageRequest(@NotNull Long endpointId, @NotBlank String messageType,
                                            @NotBlank String businessType, Long businessId,
                                            @NotBlank String idempotencyKey, @NotNull Map<String,Object> payload) {}
    public record IntegrationCallbackRequest(@NotBlank String idempotencyKey,
                                             @NotBlank String messageType, String businessType,
                                             Long businessId, @NotNull Map<String,Object> payload) {}
    public record BankReceiptAllocation(@NotNull Long receivableId,
                                        @NotNull @Positive @Digits(integer=16, fraction=2) BigDecimal amount) {}
    public record BankReceiptRequest(@NotNull Long endpointId, @NotBlank String bankTxnNo,
                                     String accountNoMasked, @NotNull LocalDateTime transactionTime,
                                     @NotBlank String direction,
                                     @NotNull @Positive @Digits(integer=16, fraction=2) BigDecimal amount,
                                     String counterpartyName, String purposeText,
                                     Long projectId, Long contractId, Long customerId, Long fundAccountId,
                                     List<@Valid BankReceiptAllocation> allocations,
                                     @NotNull Map<String,Object> rawPayload) {}
    public record CashForecastRequest(Long projectId, @NotNull LocalDate forecastDate,
                                      @NotBlank String scenario,
                                      @NotNull @PositiveOrZero BigDecimal inflowAmount,
                                      @NotNull @PositiveOrZero BigDecimal outflowAmount,
                                      @PositiveOrZero BigDecimal financingAmount,
                                      @NotBlank String sourceType, Long sourceId,
                                      @DecimalMin("0") @DecimalMax("1") BigDecimal confidence) {}
    public record FundPoolRequest(@NotBlank String poolCode, @NotBlank String poolName,
                                  String currencyCode, String controlMode) {}
    public record FundPoolMemberRequest(@NotNull Long poolId, @NotNull Long companyId,
                                        @NotNull Long fundAccountId,
                                        @NotNull @Positive BigDecimal quotaAmount) {}
    public record FundPoolTransferRequest(@NotNull Long poolId, @NotNull Long fromMemberId,
                                          @NotNull Long toMemberId, @NotNull @Positive BigDecimal amount,
                                          @NotBlank String idempotencyKey, String externalTxnNo,
                                          @NotNull LocalDateTime occurredAt) {}
}
