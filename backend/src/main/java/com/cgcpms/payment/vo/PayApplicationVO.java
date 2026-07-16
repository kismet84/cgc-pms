package com.cgcpms.payment.vo;

import lombok.Data;

import java.util.List;

@Data
public class PayApplicationVO {
    private String id;
    private String tenantId;
    private String projectId;
    private String contractId;
    private String partnerId;
    private String costSubjectId;
    private String budgetLineId;
    private String expenseCategory;
    private String approvalInstanceId;
    private String integrityVersion;
    private String applyCode;
    private String applyAmount;
    private String approvedAmount;
    private String actualPayAmount;
    private String payType;
    private String payStatus;
    private String approvalStatus;
    private String applyReason;
    private String projectName;
    private String contractName;
    private String partnerName;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
    private List<PayApplicationBasisVO> basis;
}
