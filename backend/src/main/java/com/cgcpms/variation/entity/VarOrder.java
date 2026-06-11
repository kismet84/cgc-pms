package com.cgcpms.variation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("var_order")
public class VarOrder extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    @NotNull
    private Long projectId;

    private Long contractId;

    private Long partnerId;

    private String varCode;

    private String varName;

    private String varType;

    private String direction;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal reportedAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal approvedAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal confirmedAmount;

    private Integer ownerConfirmFlag;

    private Integer impactDays;

    private String approvalStatus;

    private Integer costGeneratedFlag;
}
