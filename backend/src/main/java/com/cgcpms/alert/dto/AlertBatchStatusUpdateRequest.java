package com.cgcpms.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AlertBatchStatusUpdateRequest {
    @NotEmpty(message = "预警ID列表不能为空")
    private List<Long> alertIds;

    @NotBlank(message = "处理状态不能为空")
    private String processStatus;

    private String statusRemark;
}
