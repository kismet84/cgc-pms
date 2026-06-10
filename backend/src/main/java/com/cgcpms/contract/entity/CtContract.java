package com.cgcpms.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_contract")
public class CtContract extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long orgId;

    private Long projectId;

    private Long partnerId;

    private String contractCode;

    @NotBlank
    private String contractName;

    @NotBlank
    private String contractType;

    private String partyA;

    private String partyB;

    @NotNull
    @PositiveOrZero
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal contractAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal currentAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal taxRate;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal taxAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amountWithoutTax;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate signedDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String paymentMethod;

    private String settlementMethod;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal warrantyRate;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal warrantyAmount;

    private String contractStatus;

    private String approvalStatus;
}
