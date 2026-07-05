package com.cgcpms.alert.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AlertSubscriptionUpdateRequest {
    private Map<String, Object> subscription;
}
