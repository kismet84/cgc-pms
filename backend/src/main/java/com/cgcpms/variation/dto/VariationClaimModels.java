package com.cgcpms.variation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class VariationClaimModels {
    private VariationClaimModels() {}

    public record OwnerSubmissionRequest(
            @NotBlank @Size(max = 128) String externalDocumentNo,
            @NotNull LocalDateTime submittedAt,
            @Size(max = 500) String remark) {}

    public record OwnerReviewLine(
            @NotNull Long submissionItemId,
            @NotNull @DecimalMin("0") BigDecimal confirmedAmount,
            @Size(max = 500) String reductionReason) {}

    public record OwnerReviewRequest(
            @NotBlank String conclusion,
            @NotBlank @Size(max = 128) String responseDocumentNo,
            @Size(max = 500) String responseComment,
            @NotNull LocalDateTime reviewedAt,
            @Valid List<OwnerReviewLine> items) {}
}
