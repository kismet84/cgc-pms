package com.cgcpms.cost.vo;

import lombok.Data;

// TODO: 添加 Numeric 字段与 String 字段并存（如 contractAmount+contractAmountStr），避免重复转换开销
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
    private String confirmedRevenue;
    private String expectedProfit;
    private String costDeviation;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
