package com.cgcpms.settlement.vo;

import lombok.Data;

import java.util.List;

@Data
public class StlSettlementVO {
    private String id;
    private String tenantId;
    private String projectId;
    private String contractId;
    private String partnerId;
    private String settlementCode;
    private String settlementType;
    private String contractAmount;
    private String changeAmount;
    private String measuredAmount;
    private String deductionAmount;
    private String paidAmount;
    private String finalAmount;
    private String approvalStatus;
    private String status;
    // V24 enhanced fields
    private String unpaidAmount;
    private String warrantyAmount;
    private String settlementStatus;
    private String finalizedAt;
    // Display names
    private String projectName;
    private String contractName;
    private String partnerName;
    // Audit fields
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
    // Items
    private List<StlSettlementItemVO> items;
}
