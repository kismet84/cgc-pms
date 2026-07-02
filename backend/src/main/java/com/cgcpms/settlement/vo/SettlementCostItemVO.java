package com.cgcpms.settlement.vo;

import lombok.Data;

@Data
public class SettlementCostItemVO {
    private String id;
    private String costSubjectId;
    private String costSubjectName;
    private String costType;
    private String sourceType;
    private String sourceId;
    private String sourceItemId;
    private String amount;
    private String taxAmount;
    private String amountWithoutTax;
    private String costDate;
    private String costStatus;
}
