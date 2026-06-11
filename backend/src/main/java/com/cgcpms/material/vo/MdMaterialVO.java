package com.cgcpms.material.vo;

import lombok.Data;

@Data
public class MdMaterialVO {
    private String id;
    private String tenantId;
    private String materialCode;
    private String materialName;
    private String categoryId;
    private String specification;
    private String unit;
    private String brand;
    private String defaultTaxRate;
    private String status;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
