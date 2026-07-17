package com.cgcpms.financeclose.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FinancialCloseModels {
    private FinancialCloseModels() {}

    public record PeriodRequest(@Min(2000) int fiscalYear, @Min(1) @Max(12) int fiscalMonth) {}
    public record CloseRequest(String comment) {}
    public record ReopenRequest(@NotBlank String reason) {}
    public record ReviewRequest(boolean approved, String comment) {}
    public record BankResolveRequest(@NotBlank String businessType, @NotNull Long businessId,
                                     @NotNull Long cashJournalId, @NotBlank String note) {}
    public record AdjustmentLine(@NotBlank String direction, String accountCode, String accountName,
                                 Long costSubjectId, @NotNull @Positive BigDecimal amount,
                                 @NotBlank String summary) {}
    public record AdjustmentRequest(@NotNull LocalDate entryDate, Long projectId, Long contractId,
                                    @NotBlank String reason, @NotEmpty List<@Valid AdjustmentLine> lines) {}
}
