package com.cgcpms.purchase.vo;

import lombok.Data;

import java.util.List;

@Data
public class MatPurchaseRequestVO {
    private String id;
    private String tenantId;
    private String projectId;
    private String projectName;
    private String contractId;
    private String contractName;
    private String purpose;
    private String requestCode;
    private String approvalStatus;
    private String status;
    private String createdBy;
    private String createdTime;
    private String updatedTime;
    private String remark;
    private List<MatPurchaseRequestItemVO> items;
}
