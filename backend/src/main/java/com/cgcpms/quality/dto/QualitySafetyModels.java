package com.cgcpms.quality.dto;

import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.quality.entity.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class QualitySafetyModels {
    private QualitySafetyModels() {}

    public record PlanCommand(
            @NotNull Long projectId,
            @NotBlank @Size(max = 64) String planCode,
            @NotBlank @Size(max = 200) String planName,
            @NotBlank String inspectionType,
            @NotBlank String frequencyType,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull Long ownerUserId,
            @Size(max = 500) String remark) {}

    public record InspectionCommand(
            @NotNull Long planId,
            @NotBlank @Size(max = 64) String inspectionCode,
            @NotNull LocalDate inspectionDate,
            @NotBlank @Size(max = 200) String location,
            @NotNull Long inspectorUserId,
            @NotBlank @Size(max = 1000) String summary,
            @Size(max = 500) String remark) {}

    public record IssueCommand(
            @NotNull Long inspectionId,
            @NotBlank @Size(max = 100) String category,
            @NotBlank String severity,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank String responsibleKind,
            Long responsiblePartnerId,
            @NotNull Long responsibleUserId,
            @NotNull LocalDate dueDate,
            @Size(max = 500) String remark) {}

    public record RectificationCommand(
            @NotNull Long issueId,
            @NotBlank @Size(max = 2000) String actionDescription,
            @NotNull Long responsibleUserId,
            @NotNull LocalDate plannedCompleteDate,
            @Size(max = 500) String remark) {}

    public record ReinspectionCommand(
            @NotBlank String result,
            @NotBlank @Size(max = 1000) String comment) {}

    public record ConsequenceCommand(
            @NotNull Long issueId,
            @NotNull Long partnerId,
            Long contractId,
            @NotBlank @Size(max = 64) String consequenceCode,
            @NotBlank String decisionType,
            @NotNull @DecimalMin("0.00") BigDecimal fineAmount,
            @NotNull @DecimalMin("0.00") BigDecimal reworkCostAmount,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal evaluationScore,
            @NotBlank @Size(max = 1000) String evaluationComment,
            @Size(max = 500) String remark) {}

    public record Trace(
            QualityInspectionPlan plan,
            QualityInspectionRecord inspection,
            QualitySafetyIssue issue,
            List<QualityRectification> rectifications,
            QualityConsequence consequence,
            QualityPartnerEvaluation evaluation,
            CostItem costItem) {}
}
