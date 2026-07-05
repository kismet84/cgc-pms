package com.cgcpms.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AlertOperationResponse {
    private boolean success;
    private Long alertId;
    private String processStatus;

    public AlertOperationResponse(boolean success, Long alertId) {
        this(success, alertId, null);
    }
}
