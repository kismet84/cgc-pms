package com.cgcpms.alert.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AlertAcknowledgeRequest {
    @Size(max = 500, message = "接单说明不能超过500个字符")
    private String remark;
}
