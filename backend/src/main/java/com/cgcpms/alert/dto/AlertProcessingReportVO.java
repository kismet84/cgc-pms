package com.cgcpms.alert.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AlertProcessingReportVO {
    private long totalCount;
    private long unreadCount;
    private long readCount;
    private long unacknowledgedCount;
    private long overdueOpenCount;
    private long escalatedCount;
    private long failedNotificationCount;
    private Map<String, Long> severityCounts = new LinkedHashMap<>();
    private Map<String, Long> processStatusCounts = new LinkedHashMap<>();
}
