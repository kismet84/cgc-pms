package com.cgcpms.site.vo;

import lombok.Data;

@Data
public class SiteDailyPlannedTaskVO {
    private String id;
    private String taskCode;
    private String taskName;
    private String workArea;
    private String plannedStartDate;
    private String plannedEndDate;
    private String status;
    private String progressPercent;
}
