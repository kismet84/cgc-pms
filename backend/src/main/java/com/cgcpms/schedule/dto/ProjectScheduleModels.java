package com.cgcpms.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ProjectScheduleModels {
    private ProjectScheduleModels() {}

    public record ScheduleRequest(
            @NotNull Long projectId,
            @NotBlank @Size(max = 64) String planCode,
            @NotBlank @Size(max = 200) String planName,
            @NotNull LocalDate plannedStartDate,
            @NotNull LocalDate plannedEndDate,
            @Size(max = 500) String remark) {}

    public record WbsTaskRequest(
            @NotBlank @Size(max = 64) String taskCode,
            @NotBlank @Size(max = 200) String taskName,
            @Size(max = 64) String parentTaskCode,
            @Size(max = 64) String predecessorTaskCode,
            @Size(max = 200) String workArea,
            Long responsibleUserId,
            @NotNull LocalDate plannedStartDate,
            @NotNull LocalDate plannedEndDate,
            @NotNull @DecimalMin(value = "0.0001") @DecimalMax(value = "100") BigDecimal weightPercent,
            @DecimalMin(value = "0") BigDecimal plannedQuantity,
            @Size(max = 30) String unit,
            @Size(max = 500) String remark) {}

    public record WbsTaskBatch(
            @NotNull @Min(0) Integer expectedVersion,
            @NotEmpty List<@Valid WbsTaskRequest> tasks) {}

    public record PeriodPlanRequest(
            @NotNull Long schedulePlanId,
            @Pattern(regexp = "MONTHLY|WEEKLY") String periodType,
            Long parentPeriodPlanId,
            @NotBlank @Size(max = 64) String periodCode,
            @NotBlank @Size(max = 200) String periodName,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 500) String remark) {}

    public record PeriodItemRequest(
            @NotNull Long wbsTaskId,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal targetProgress,
            @DecimalMin("0") BigDecimal plannedQuantity) {}

    public record PeriodItemBatch(
            @NotNull @Min(0) Integer expectedVersion,
            @NotEmpty List<@Valid PeriodItemRequest> items) {}

    public record DailyProgressRequest(
            @NotNull Long wbsTaskId,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal currentProgress,
            @NotNull @DecimalMin("0") BigDecimal completedQuantity,
            @NotBlank @Size(max = 500) String workDescription) {}

    public record DailyProgressBatch(@NotEmpty List<@Valid DailyProgressRequest> items) {}

    public record CorrectiveActionRequest(
            @NotNull Long snapshotId,
            @NotBlank @Size(max = 64) String actionCode,
            @NotBlank @Size(max = 500) String reason,
            @NotBlank @Size(max = 1000) String actionPlan,
            @NotNull Long responsibleUserId,
            @NotNull LocalDate dueDate,
            @Size(max = 500) String remark) {}
}
