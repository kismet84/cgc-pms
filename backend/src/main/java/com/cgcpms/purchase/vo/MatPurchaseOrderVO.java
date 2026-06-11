package com.cgcpms.purchase.vo;

import lombok.Data;

import java.util.List;

@Data
public class MatPurchaseOrderVO {
    private String id;
    private String tenantId;
    private String projectId;
    private String projectName;
    private String requestId;
    private String contractId;
    private String contractName;
    private String partnerId;
    private String partnerName;
    private String orderCode;
    private String orderType;
    private String orderDate;
    private String deliveryDate;
    private String totalAmount;
    private String approvalStatus;
    private String orderStatus;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
    private List<MatPurchaseOrderItemVO> items;
}
