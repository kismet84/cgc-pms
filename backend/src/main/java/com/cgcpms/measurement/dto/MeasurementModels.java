package com.cgcpms.measurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class MeasurementModels {
    private MeasurementModels() {}

    public record PeriodRequest(
            @NotNull Long projectId, @NotNull Long contractId,
            @NotBlank String periodCode, @NotBlank String periodName,
            @NotNull LocalDate startDate, @NotNull LocalDate endDate,
            @NotNull LocalDate cutoffDate, String remark) {}

    public record MeasurementLineRequest(
            Long contractItemId, Long contractChangeId,
            @NotNull @Positive @Digits(integer = 14, fraction = 4) BigDecimal currentQuantity,
            @NotNull @Min(0) Integer evidenceCount) {}

    public record MeasurementRequest(
            @NotNull Long projectId, @NotNull Long contractId, @NotNull Long periodId,
            @NotNull LocalDate measureDate, @NotNull @Min(0) Integer attachmentCount,
            @NotEmpty List<@Valid MeasurementLineRequest> lines, String remark) {}

    public record OwnerSubmissionRequest(
            String externalDocumentNo, @NotNull @Min(1) Integer attachmentCount,
            String remark) {}

    public record OwnerReviewLineRequest(
            @NotNull Long measurementLineId,
            @NotNull @PositiveOrZero @Digits(integer = 14, fraction = 4) BigDecimal confirmedQuantity,
            String deductionReason) {}

    public record OwnerReviewRequest(
            @NotBlank String decision, @NotBlank String reviewerName, String reviewComment,
            LocalDate settlementDate, LocalDate dueDate,
            @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal taxAmount,
            @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal retentionAmount,
            @Min(1) Integer attachmentCount,
            List<@Valid OwnerReviewLineRequest> lines) {}
}
