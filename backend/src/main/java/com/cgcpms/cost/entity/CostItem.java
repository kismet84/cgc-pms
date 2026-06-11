package com.cgcpms.cost.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_item")
public class CostItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long orgId;

    private Long projectId;

    private Long contractId;

    private Long partnerId;

    private Long costSubjectId;

    private String costType;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal taxAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amountWithoutTax;

    private String sourceType;

    private Long sourceId;

    private Long sourceItemId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate costDate;

    private String costStatus;

    private Integer generatedFlag;
}
