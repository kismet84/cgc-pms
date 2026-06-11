package com.cgcpms.variation.vo;

import lombok.Data;

@Data
public class VarOrderItemVO {
    private String id;
    private String tenantId;
    private String varOrderId;
    private String itemName;
    private String unit;
    private String quantity;
    private String unitPrice;
    private String amount;
    private String costSubjectId;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
