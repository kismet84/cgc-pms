package com.cgcpms.cashforecast.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class CashForecastModels {
    private CashForecastModels() {}

    public record CycleRequest(@NotNull Long projectId, @NotBlank String forecastName,
                               @NotNull LocalDate asOfDate, @NotNull LocalDate horizonStart,
                               @NotNull LocalDate horizonEnd, @NotBlank String scenario,
                               @NotNull @PositiveOrZero @Digits(integer=16, fraction=2) BigDecimal openingBalance,
                               Long previousCycleId) {}
    public record ApprovalRequest(boolean approved, @NotBlank String comment) {}
    public record RollRequest(@NotNull LocalDate asOfDate, @NotNull LocalDate horizonEnd,
                              @NotBlank String forecastName) {}
    public record FundingActionRequest(@NotNull Long lineId, @NotBlank String actionType,
                                       @NotNull LocalDate plannedDate,
                                       @NotNull @Positive @Digits(integer=16, fraction=2) BigDecimal amount,
                                       @NotBlank String reason, String sourceType, Long sourceId) {}
    public record FundingActionApprovalRequest(boolean approved, @NotBlank String comment) {}
    public record FundingActionCompletionRequest(@NotNull @Positive @Digits(integer=16, fraction=2) BigDecimal actualAmount,
                                                  @NotBlank String completionReference) {}
}
