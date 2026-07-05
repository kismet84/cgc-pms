package com.cgcpms.alert.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AlertStatusUpdateRequest {
    @NotBlank(message = "处理状态不能为空")
    private String processStatus;

    private String statusRemark;
}
