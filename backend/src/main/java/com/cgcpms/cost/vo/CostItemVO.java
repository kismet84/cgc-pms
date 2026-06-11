package com.cgcpms.cost.vo;

import lombok.Data;

@Data
public class CostItemVO {
    private String id;
    private String tenantId;
    private String orgId;
    private String projectId;
    private String contractId;
    private String partnerId;
    private String costSubjectId;
    private String costType;
    private String amount;
    private String taxAmount;
    private String amountWithoutTax;
    private String sourceType;
    private String sourceId;
    private String sourceItemId;
    private String costDate;
    private String costStatus;
    private String generatedFlag;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
