package com.cgcpms.subcontract.vo;

import lombok.Data;

@Data
public class SubMeasureItemVO {
    private String id;
    private String tenantId;
    private String measureId;
    private String contractItemId;
    private String itemName;
    private String unit;
    private String contractQuantity;
    private String currentQuantity;
    private String cumulativeQuantity;
    private String unitPrice;
    private String amount;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
