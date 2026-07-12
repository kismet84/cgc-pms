package com.cgcpms.site.vo;

import lombok.Data;

import java.util.List;

@Data
public class SiteDailyLogVO {
    private String id;
    private String projectId;
    private String projectName;
    private String reportDate;
    private String constructionContent;
    private String issuesDelays;
    private String nextDayPlan;
    private String weatherSummary;
    private Integer onSiteHeadcount;
    private List<SiteDailyDeliveryVO> deliveries;
    private List<SiteDailyPlannedTaskVO> plannedTasks;
    private List<SiteDailyAuditEntryVO> auditTrail;
    private String status;
    private String submittedBy;
    private String submittedAt;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
}
