package com.cgcpms.requisition.vo;

import lombok.Data;

@Data
public class MatRequisitionItemVO {
    private String id;
    private String tenantId;
    private String requisitionId;
    private String materialId;
    private String materialName;
    private String specification;
    private String unit;
    private String quantity;
    private String unitPrice;
    private String amount;
    private String useLocation;
    private String batchNo;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
