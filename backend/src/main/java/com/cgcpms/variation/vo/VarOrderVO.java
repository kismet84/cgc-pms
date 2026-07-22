package com.cgcpms.variation.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class VarOrderVO {
    private String id;
    private String tenantId;
    private String projectId;
    private String contractId;
    private String partnerId;
    private String varCode;
    private String varName;
    private String eventDate;
    private String claimDeadline;
    private String eventDescription;
    private String causeCategory;
    private String responsibleParty;
    private String businessMatterKey;
    private String varType;
    private String direction;
    private String reportedAmount;
    private String approvedAmount;
    private String confirmedAmount;
    private String estimatedCostAmount;
    private Integer ownerConfirmFlag;
    private String ownerStatus;
    private String internalApprovalInstanceId;
    private String generatedContractChangeId;
    private Integer impactDays;
    private String approvalStatus;
    private Integer costGeneratedFlag;
    private Integer version;
    private String projectName;
    private String contractName;
    private String partnerName;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
    private List<VarOrderItemVO> items;
    private List<Map<String, Object>> ownerSubmissions;
}
