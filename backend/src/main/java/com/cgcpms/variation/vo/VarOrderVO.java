package com.cgcpms.variation.vo;

import lombok.Data;

import java.util.List;

@Data
public class VarOrderVO {
    private String id;
    private String tenantId;
    private String projectId;
    private String contractId;
    private String partnerId;
    private String varCode;
    private String varName;
    private String varType;
    private String direction;
    private String reportedAmount;
    private String approvedAmount;
    private String confirmedAmount;
    private Integer ownerConfirmFlag;
    private Integer impactDays;
    private String approvalStatus;
    private Integer costGeneratedFlag;
    private String projectName;
    private String contractName;
    private String partnerName;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
    private List<VarOrderItemVO> items;
}
