package com.cgcpms.cost.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CostControlModels {
    private CostControlModels() {}

    public record ForecastItemRequest(
            @NotNull Long costSubjectId,
            @NotNull @DecimalMin("0.00") BigDecimal estimatedRemainingAmount,
            @Size(max = 500) String remark) {}

    public record ForecastRequest(
            @NotNull Long projectId,
            @NotBlank @Size(max = 64) String forecastCode,
            @NotBlank @Size(max = 200) String forecastName,
            @NotNull LocalDate forecastDate,
            @NotEmpty @Size(max = 300) List<@Valid ForecastItemRequest> items,
            @Size(max = 500) String remark) {}

    public record CorrectiveActionRequest(
            @NotNull Long forecastId,
            @NotBlank @Size(max = 64) String actionCode,
            @NotBlank @Size(max = 200) String actionTitle,
            @NotBlank @Size(max = 500) String rootCause,
            @NotBlank @Size(max = 1000) String actionPlan,
            @NotNull @DecimalMin(value = "0.01") BigDecimal expectedSavingAmount,
            @NotNull Long responsibleUserId,
            @NotNull @FutureOrPresent LocalDate dueDate,
            @Size(max = 500) String remark) {}

    public record CorrectiveCloseRequest(
            @NotNull @DecimalMin("0.00") BigDecimal actualSavingAmount,
            @NotBlank @Size(max = 1000) String resultDescription) {}
}
