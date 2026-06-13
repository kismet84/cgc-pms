package com.cgcpms.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("stl_settlement")
public class StlSettlement extends BaseEntity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    @NotNull
    private Long projectId;

    private Long contractId;

    private Long partnerId;

    private String settlementCode;

    private String settlementType;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal contractAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal changeAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal measuredAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal deductionAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal paidAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal finalAmount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvalStatus;

    private String status;

    // V24 enhanced fields
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal unpaidAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal warrantyAmount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String settlementStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finalizedAt;
}
