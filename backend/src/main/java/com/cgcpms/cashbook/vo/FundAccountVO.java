package com.cgcpms.cashbook.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class FundAccountVO {
    private String id;
    private String accountCode;
    private String accountName;
    private String accountType;
    private String bankName;
    private String bankAccountNo;
    private LocalDate openingDate;
    private String openingBalance;
    private Integer enabledFlag;
    private Integer version;
    private String remark;
}
