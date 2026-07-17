package com.cgcpms.purchase.vo;

import lombok.Data;

@Data
public class MatPurchaseOrderItemVO {
    private String id;
    private String tenantId;
    private String orderId;
    private String requestItemId;
    private String budgetLineId;
    private String projectId;
    private String materialId;
    private String materialName;
    private String specification;
    private String unit;
    private String quantity;
    private String unitPrice;
    private String taxRate;
    private String amount;
    private String taxAmount;
    private String amountWithoutTax;
    private String receivedQuantity;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
