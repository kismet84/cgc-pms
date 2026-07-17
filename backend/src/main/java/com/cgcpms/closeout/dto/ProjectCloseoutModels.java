package com.cgcpms.closeout.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ProjectCloseoutModels {
    private ProjectCloseoutModels() {}

    public record InitiateCommand(
            @NotNull Long projectId,
            @NotBlank @Size(max = 64) String closeoutCode,
            @NotNull LocalDate plannedCompletionDate,
            @Size(max = 500) String remark) {}

    public record SectionAcceptanceCommand(
            @NotNull Long wbsTaskId,
            @NotNull Long qualityInspectionId,
            @NotBlank @Size(max = 64) String acceptanceCode,
            @NotBlank @Size(max = 200) String acceptanceName,
            @NotNull LocalDate acceptanceDate,
            @NotBlank @Pattern(regexp = "PASS|CONDITIONAL_PASS") String conclusion,
            @Size(max = 500) String remark) {}

    public record FinalAcceptanceCommand(
            @NotBlank @Size(max = 64) String acceptanceCode,
            @NotNull LocalDate acceptanceDate,
            @NotBlank @Size(max = 200) String organizer,
            @NotBlank @Size(max = 1000) String participantSummary,
            @NotBlank @Pattern(regexp = "PASS|CONDITIONAL_PASS") String conclusion,
            @NotBlank @Size(max = 2000) String acceptanceSummary,
            @Size(max = 500) String remark) {}

    public record SettlementBindingCommand(@NotNull Long ownerSettlementId) {}

    public record WarrantyCommand(
            @NotNull Long contractId,
            @NotNull Long receivableId,
            @NotBlank @Size(max = 64) String warrantyCode,
            @NotNull @DecimalMin("0.01") BigDecimal warrantyAmount,
            @NotNull LocalDate warrantyStartDate,
            @NotNull LocalDate warrantyEndDate,
            @NotNull Long responsibleUserId,
            @Size(max = 500) String remark) {}

    public record DefectCommand(
            @NotBlank @Size(max = 64) String defectCode,
            @NotBlank @Size(max = 200) String defectTitle,
            @NotBlank @Size(max = 2000) String defectDescription,
            @NotNull Long responsibleUserId,
            @NotNull LocalDate rectificationDeadline,
            @Size(max = 500) String remark) {}

    public record RectificationCommand(@NotBlank @Size(max = 2000) String rectificationContent) {}

    public record DefectVerificationCommand(
            @NotBlank @Pattern(regexp = "ACCEPTED|REJECTED") String decision,
            @NotBlank @Size(max = 1000) String verificationComment) {}

    public record ArchiveTransferCommand(
            @NotBlank @Size(max = 64) String transferCode,
            @NotNull LocalDate transferDate,
            @NotBlank @Size(max = 200) String recipientOrganization,
            @NotBlank @Size(max = 100) String recipientName,
            @NotBlank @Size(max = 300) String archiveLocation,
            @NotBlank @Size(max = 2000) String transferScope,
            @Size(max = 500) String remark) {}

    public record CloseProjectCommand(
            @NotNull LocalDate actualCompletionDate,
            @NotBlank @Size(max = 500) String reason) {}
}
