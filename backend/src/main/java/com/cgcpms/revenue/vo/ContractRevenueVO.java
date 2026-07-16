package com.cgcpms.revenue.vo;

import lombok.Data;

/**
 * 收入确认视图对象
 */
@Data
public class ContractRevenueVO {

    private String id;
    private String tenantId;
    private String projectId;
    private String projectName;
    private String contractId;
    private String contractName;
    private String revenueCode;
    private String revenueDate;
    private String progressPercent;
    private String progressDesc;
    private String revenueAmount;
    private String revenueTax;
    private String revenueAmountWithTax;
    private String billedAmount;
    private String billedTax;
    private String approvalStatus;
    private String costItemId;
    private String approvalInstanceId;
    private String formulaVersion;
    private Integer attachmentCount;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
}
