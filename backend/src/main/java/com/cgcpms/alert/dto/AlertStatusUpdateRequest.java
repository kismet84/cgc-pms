package com.cgcpms.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AlertStatusUpdateRequest {
    @NotBlank(message = "处理状态不能为空")
    private String processStatus;

    @NotBlank(message = "处理说明不能为空")
    @Size(max = 500, message = "处理说明不能超过500个字符")
    private String statusRemark;
}
