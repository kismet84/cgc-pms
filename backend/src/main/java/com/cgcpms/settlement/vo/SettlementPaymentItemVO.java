package com.cgcpms.settlement.vo;

import lombok.Data;

@Data
public class SettlementPaymentItemVO {
    private String id;
    private String applicationId;
    private String applyCode;
    private String payType;
    private String applyAmount;
    private String approvedAmount;
    private String actualPayAmount;
    private String payStatus;
    private String payDate;
    private String voucherNo;
    private String createdAt;
}
