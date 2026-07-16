package com.cgcpms.budget.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProjectBudgetVO {
    private String id;
    private String projectId;
    private String versionNo;
    private String budgetName;
    private String totalAmount;
    private String approvalStatus;
    private String status;
    private boolean active;
    private String effectiveAt;
    private Integer version;
    private String createdAt;
    private String updatedAt;
    private String remark;
    private List<BudgetLineVO> lines;

    @Data
    public static class BudgetLineVO {
        private String id;
        private String costSubjectId;
        private String costSubjectName;
        private String budgetAmount;
        private String reservedAmount;
        private String consumedAmount;
        private String availableAmount;
        private Integer version;
        private String remark;
    }
}
