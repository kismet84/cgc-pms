package com.cgcpms.receipt.vo;

import lombok.Data;

import java.util.List;

@Data
public class MatReceiptVO {
    private String id;
    private String tenantId;
    private String projectId;
    private String projectName;
    private String orderId;
    private String orderCode;
    private String contractId;
    private String contractName;
    private String partnerId;
    private String partnerName;
    private String receiptCode;
    private String receiptDate;
    private String warehouseId;
    private String receiverId;
    private String qualityStatus;
    private String totalAmount;
    private String approvalStatus;
    private Integer costGeneratedFlag;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
    private List<MatReceiptItemVO> items;
}
