package com.cgcpms.cost.vo;

import lombok.Data;

/**
 * Cost ledger VO with resolved names from related entities.
 * All fields are String for frontend compatibility.
 */
@Data
public class CostLedgerVO {
    private String id;
    private String projectId;
    private String projectName;
    private String contractId;
    private String contractName;
    private String partnerId;
    private String partnerName;
    private String costSubjectId;
    private String costSubjectName;
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
    private String remark;
}
