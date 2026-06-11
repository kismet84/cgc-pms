package com.cgcpms.payment.vo;

import lombok.Data;

@Data
public class PayApplicationBasisVO {
    private String id;
    private String tenantId;
    private String payApplicationId;
    private String basisType;
    private String basisId;
    private String basisAmount;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
