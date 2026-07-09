package com.cgcpms.workflow.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class WfEfficiencyVO {

    private long pendingCount;
    private long doneCount;
    private long overduePendingCount;
    private long handledTaskCount;
    private long averageHandleMinutes;
    private int overdueHours;
    private Map<String, Long> instanceStatusCounts = new LinkedHashMap<>();
}
