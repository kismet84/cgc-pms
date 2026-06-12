package com.cgcpms.inventory.vo;

import lombok.Data;

@Data
public class MatWarehouseVO {
    private String id;
    private String tenantId;
    private String projectId;
    private String warehouseCode;
    private String warehouseName;
    private String status;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
