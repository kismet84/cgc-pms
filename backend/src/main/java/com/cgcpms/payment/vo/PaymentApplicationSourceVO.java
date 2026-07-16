package com.cgcpms.payment.vo;

import lombok.Data;

@Data
public class PaymentApplicationSourceVO {
    private String id;
    private String payApplicationId;
    private String sourceType;
    private String sourceRefId;
    private String expenseId;
    private String settlementId;
    private String sourceAmount;
    private String paidAmount;
    private String remark;
}
