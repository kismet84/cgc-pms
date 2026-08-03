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
    private String taxInclusiveInfoPrice;
    private String infoPricePeriod;
    private String infoPriceSource;
    private String infoPriceVerificationStatus;
    private String infoPriceExternalRowKey;
    private Integer infoPriceReviewRequired;
    private String purchasePrice;
    private String purchasePriceReceiptItemId;
    private String purchasePriceDate;
    private String status;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
