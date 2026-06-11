package com.cgcpms.cost.vo;

import lombok.Data;

@Data
public class CostSummaryVO {
    private String id;
    private String tenantId;
    private String projectId;
    private String projectName;
    private String summaryDate;
    private String costSubjectId;
    private String costSubjectName;
    private String targetCost;
    private String contractLockedCost;
    private String actualCost;
    private String paidAmount;
    private String estimatedRemainingCost;
    private String dynamicCost;
    private String contractIncome;
    private String expectedProfit;
    private String costDeviation;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
