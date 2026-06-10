package com.cgcpms.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_contract_payment_term")
public class CtContractPaymentTerm extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long contractId;

    @NotBlank
    private String termName;

    @PositiveOrZero
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal paymentRatio;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal paymentAmount;

    private String paymentCondition;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate plannedDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate actualDate;

    private String termStatus;

    private Integer sortOrder;
}
