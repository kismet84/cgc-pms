package com.cgcpms.alert.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AlertProcessingReportVO {
    private long totalCount;
    private long unreadCount;
    private long readCount;
    private Map<String, Long> severityCounts = new LinkedHashMap<>();
    private Map<String, Long> processStatusCounts = new LinkedHashMap<>();
}
