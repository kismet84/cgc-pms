package com.cgcpms.expense.vo;

import lombok.Data;

@Data
public class ExpenseApplicationVO {
    private String id;
    private String projectId;
    private String contractId;
    private String costSubjectId;
    private String budgetLineId;
    private String payeePartnerId;
    private String expenseCode;
    private String expenseCategory;
    private String expenseDate;
    private String amount;
    private String convertedAmount;
    private String paidAmount;
    private String availableToConvert;
    private String description;
    private String status;
    private String approvalStatus;
    private Integer version;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
