package com.cgcpms.receipt.vo;

import lombok.Data;

@Data
public class MatReceiptItemVO {
    private String id;
    private String tenantId;
    private String receiptId;
    private String orderItemId;
    private String materialId;
    private String materialName;
    private String specification;
    private String unit;
    private String actualQuantity;
    private String qualifiedQuantity;
    private String unitPrice;
    private String amount;
    private String useLocation;
    private String batchNo;
    /** Order item quantity for receipt line selection context */
    private String orderedQuantity;
    /** Already received quantity from order items */
    private String receivedQuantity;
    /** Remaining quantity = orderedQty - receivedQty */
    private String remainingQuantity;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
