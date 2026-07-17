package com.cgcpms.purchase.vo;

import lombok.Data;

@Data
public class MatPurchaseRequestItemVO {
    private String id;
    private String tenantId;
    private String requestId;
    private String materialId;
    private String materialName;
    private String wbsTaskId;
    private String budgetLineId;
    private String quantity;
    private String unit;
    private String plannedDate;
    private String createdBy;
    private String createdTime;
    private String updatedTime;
    private String remark;
}
