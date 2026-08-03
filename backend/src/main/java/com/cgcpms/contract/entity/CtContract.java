package com.cgcpms.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Digits;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_contract")
public class CtContract extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    private Long orgId;

    private Long projectId;


    private String contractCode;

    @NotBlank
    private String contractName;

    @NotBlank
    private String contractType;

    @NotNull(message = "合同甲方不能为空")
    private Long partyAId;

    @NotNull(message = "合同乙方不能为空")
    private Long partyBId;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 16, fraction = 2)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal contractAmount;

    @Digits(integer = 16, fraction = 2)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal currentAmount;

    @Digits(integer = 16, fraction = 2)
    @JsonSerialize(using = ToStringSerializer.class)
    // paidAmount 表示已付累计金额，允许为负以处理退款/冲销业务场景
    private BigDecimal paidAmount;

    /** 已审批合格验收减有效合格品退货的净应付缓存。 */
    @Digits(integer = 16, fraction = 2)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal payableAmount;

    private String pricingMode;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal taxRate;

    @Digits(integer = 16, fraction = 2)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal taxAmount;

    @Digits(integer = 16, fraction = 2)
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

    private String contractStatus;

    private String approvalStatus;

    @PositiveOrZero
    @Digits(integer = 16, fraction = 2)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal settlementAmount;

    private Integer costGeneratedFlag;

    @Version
    private Integer version;
}
