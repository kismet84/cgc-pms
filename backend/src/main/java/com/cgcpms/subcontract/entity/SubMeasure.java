package com.cgcpms.subcontract.entity;

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
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sub_measure")
public class SubMeasure extends BaseEntity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    @NotNull
    private Long projectId;

    private Long contractId;

    private Long partnerId;

    private Long subTaskId;

    private String measureCode;

    private String measurePeriod;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate measureDate;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal reportedAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal approvedAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal deductionAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal netAmount;

    private String approvalStatus;

    private Integer costGeneratedFlag;

    private String status;
}
