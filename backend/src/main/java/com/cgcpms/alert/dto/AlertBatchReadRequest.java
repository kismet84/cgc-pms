package com.cgcpms.alert.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AlertBatchReadRequest {
    @NotEmpty(message = "预警ID列表不能为空")
    private List<Long> alertIds;
}
