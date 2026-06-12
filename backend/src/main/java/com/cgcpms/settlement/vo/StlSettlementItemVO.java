package com.cgcpms.settlement.vo;

import lombok.Data;

@Data
public class StlSettlementItemVO {
    private String id;
    private String tenantId;
    private String settlementId;
    private String itemName;
    private String unit;
    private String quantity;
    private String unitPrice;
    private String amount;
    private String costSubjectId;
    // V24 enhanced fields
    private String sourceType;
    private String sourceId;
    // Audit fields
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
