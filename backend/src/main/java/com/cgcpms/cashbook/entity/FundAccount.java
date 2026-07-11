package com.cgcpms.cashbook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_account")
public class FundAccount extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    private String accountCode;
    private String accountName;
    private String accountType;
    private String bankName;
    private String bankAccountNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate openingDate;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal openingBalance;

    private Integer enabledFlag;

    @Version
    private Integer version;
}
