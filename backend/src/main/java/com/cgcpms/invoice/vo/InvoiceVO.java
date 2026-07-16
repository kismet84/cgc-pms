package com.cgcpms.invoice.vo;

import lombok.Data;

@Data
public class InvoiceVO {
    private String id;
    private String tenantId;
    private String payRecordId;
    private String payApplicationId;
    private String projectId;
    private String contractId;
    private String partnerId;
    private String documentType;
    private String integrityVersion;
    private String invoiceNo;
    private String invoiceType;
    private String invoiceAmount;
    private String taxRate;
    private String taxAmount;
    private String invoiceDate;
    private String verifyStatus;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
    private String sellerName;
    private String buyerName;
    private String buyerTaxNo;
    private String sellerTaxNo;
    private String exceptionStatus;
    private String exceptionReason;
}
