package com.cgcpms.payment.vo;

import lombok.Data;

@Data
public class PayRecordVO {
    private String id;
    private String tenantId;
    private String payApplicationId;
    private String contractId;
    private String partnerId;
    private String payAmount;
    private String payDate;
    private String payMethod;
    private String voucherNo;
    private String payStatus;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
