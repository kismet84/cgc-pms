package com.cgcpms.alert.dto;

import lombok.Data;

@Data
public class AlertRuleEffectVO {
    private String ruleType;
    private long generatedCount;
    private long acknowledgedCount;
    private long withinResponseSlaCount;
    private long escalatedCount;
    private long processedCount;
    private long archivedCount;
    private long invalidCount;
    private long failedNotificationCount;
    private Long averageResponseMinutes;
}
