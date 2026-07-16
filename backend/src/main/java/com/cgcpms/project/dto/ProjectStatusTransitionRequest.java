package com.cgcpms.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectStatusTransitionRequest {
    @NotBlank(message = "目标状态不能为空")
    private String targetStatus;
    @NotBlank(message = "状态变更原因不能为空")
    private String reason;
}
