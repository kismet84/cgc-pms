package com.cgcpms.contract.vo;

import lombok.Data;

@Data
public class CtContractVO {
    private String id;
    private String tenantId;
    private String orgId;
    private String projectId;
    private String partnerId;
    private String contractCode;
    private String contractName;
    private String contractType;
    private String partyA;
    private String partyB;
    private String contractAmount;
    private String currentAmount;
    private String taxRate;
    private String taxAmount;
    private String amountWithoutTax;
    private String signedDate;
    private String startDate;
    private String endDate;
    private String paymentMethod;
    private String settlementMethod;
    private String warrantyRate;
    private String warrantyAmount;
    private String paidAmount;
    private String settlementAmount;
    private String contractStatus;
    private String approvalStatus;
    private String projectName;
    private String partnerName;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
