package com.cgcpms.alert.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AlertBatchReadRequest {
    @NotEmpty(message = "预警ID列表不能为空")
    @Size(max = 200, message = "批量预警不能超过200条")
    private List<Long> alertIds;
}
