package com.cgcpms.tech.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TechnicalManagementModels {
    private TechnicalManagementModels() {}

    public record SchemeCommand(
            @NotNull Long projectId,
            @NotBlank @Size(max = 64) String schemeCode,
            @NotBlank @Size(max = 200) String schemeName,
            @NotBlank @Pattern(regexp = "GENERAL|SPECIAL|CONSTRUCTION_ORGANIZATION|METHOD_STATEMENT") String schemeType,
            @NotNull Long responsibleUserId,
            @NotNull @FutureOrPresent LocalDate plannedEffectiveDate,
            @Size(max = 500) String remark) {}

    public record DrawingReceiptCommand(
            @NotNull Long projectId,
            @NotBlank @Size(max = 64) String drawingCode,
            @NotBlank @Size(max = 200) String drawingName,
            @NotBlank @Size(max = 50) String specialty,
            @NotBlank @Size(max = 200) String sourceOrganization,
            @NotBlank @Size(max = 30) String versionNo,
            @NotNull LocalDateTime receivedAt,
            @Size(max = 500) String changeSummary,
            @Size(max = 500) String remark) {}

    public record DrawingVersionCommand(
            @NotBlank @Size(max = 30) String versionNo,
            @NotNull Long previousVersionId,
            @NotNull Long sourceRfiId,
            @NotNull LocalDateTime receivedAt,
            @NotBlank @Size(max = 500) String changeSummary,
            @Size(max = 500) String remark) {}

    public record ReviewCommand(
            @NotBlank @Size(max = 64) String reviewCode,
            @NotNull LocalDate reviewDate,
            @NotNull Long chairUserId,
            @NotBlank @Size(max = 500) String participantSummary,
            @NotBlank @Pattern(regexp = "PASS|CONDITIONAL|REJECTED") String conclusion,
            @NotBlank @Size(max = 1000) String reviewSummary,
            boolean requiresRfi,
            @Size(max = 500) String remark) {}

    public record RfiCommand(
            @NotBlank @Size(max = 64) String rfiCode,
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 2000) String question,
            @NotBlank @Pattern(regexp = "NORMAL|HIGH|URGENT") String priority,
            @NotNull @FutureOrPresent LocalDate responseDueDate,
            @Size(max = 500) String remark) {}

    public record RfiResponseCommand(
            @NotBlank @Size(max = 2000) String responseContent,
            boolean changeRequired,
            @NotBlank @Size(max = 100) String responderName) {}

    public record ResponseReviewCommand(
            @NotBlank @Pattern(regexp = "ACCEPTED|REJECTED") String decision,
            @NotBlank @Size(max = 500) String reviewComment) {}

    public record DisclosureCommand(
            @NotNull Long drawingVersionId,
            Long schemeId,
            @NotBlank @Size(max = 64) String disclosureCode,
            @NotBlank @Size(max = 200) String disclosureTitle,
            @NotNull LocalDate disclosureDate,
            @NotNull Long presenterUserId,
            @NotBlank @Size(max = 500) String recipientSummary,
            @NotBlank @Size(max = 2000) String disclosureContent,
            @Size(max = 500) String remark) {}

    public record ConstructionReferenceCommand(
            @NotNull Long disclosureId,
            @NotNull Long dailyLogId,
            @NotNull Long wbsTaskId,
            @NotNull LocalDate referenceDate,
            @NotBlank @Size(max = 200) String workArea,
            @NotBlank @Size(max = 1000) String referenceDescription,
            @Size(max = 500) String remark) {}

    public record ArchiveCommand(
            @NotNull Long constructionReferenceId,
            @NotNull Long qualityInspectionId,
            @NotBlank @Size(max = 64) String archiveCode,
            @NotNull LocalDate acceptanceDate,
            @NotBlank @Pattern(regexp = "PASS|CONDITIONAL_PASS") String acceptanceConclusion,
            @NotBlank @Size(max = 300) String archiveLocation,
            @Size(max = 500) String remark) {}
}
