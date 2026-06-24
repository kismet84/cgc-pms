package com.cgcpms.requisition.vo;

import lombok.Data;

import java.util.List;

@Data
public class MatRequisitionVO {
    private String id;
    private String tenantId;
    private String projectId;
    private String projectName;
    private String contractId;
    private String contractName;
    private String partnerId;
    private String partnerName;
    private String requisitionCode;
    private String requisitionDate;
    private String warehouseId;
    private String requisitionerId;
    private String approvalStatus;
    private String totalAmount;
    private String stockOutFlag;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
    private List<MatRequisitionItemVO> items;
}
