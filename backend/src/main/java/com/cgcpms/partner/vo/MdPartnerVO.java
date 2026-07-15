package com.cgcpms.partner.vo;

import lombok.Data;

@Data
public class MdPartnerVO {

    private String id;
    private String partnerCode;
    private String partnerName;
    private String partnerType;
    private String creditCode;
    private String legalPerson;
    private String contactName;
    private String contactPhone;
    private String bankName;
    private String bankAccount;
    private String qualificationLevel;
    private Integer blacklistFlag;
    private String riskLevel;
    private String status;
    private Integer defaultLeadDays;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
