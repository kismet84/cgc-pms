package com.cgcpms.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProjectMemberRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 50) String roleCode,
        @Size(max = 200) String positionName,
        LocalDate startDate,
        LocalDate endDate,
        @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        String remark) {
}
