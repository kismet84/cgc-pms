package com.cgcpms.site.vo;

import lombok.Data;

@Data
public class SiteDailyLogVO {
    private String id;
    private String projectId;
    private String projectName;
    private String reportDate;
    private String constructionContent;
    private String issuesDelays;
    private String nextDayPlan;
    private String status;
    private String submittedBy;
    private String submittedAt;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
}
