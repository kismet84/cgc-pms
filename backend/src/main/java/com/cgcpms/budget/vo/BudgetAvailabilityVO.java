package com.cgcpms.budget.vo;

import lombok.Data;

@Data
public class BudgetAvailabilityVO {
    private String budgetId;
    private String budgetLineId;
    private String projectId;
    private String costSubjectId;
    private String budgetAmount;
    private String reservedAmount;
    private String consumedAmount;
    private String availableAmount;
}
