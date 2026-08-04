package com.cgcpms.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProjectCommencementSaveRequest(
        Integer version,
        @NotNull(message = "拟开工日期不能为空") LocalDate plannedStartDate,
        @NotBlank(message = "开工依据类型不能为空") @Size(max = 32) String basisType,
        @Size(max = 500) String remark) {
}
