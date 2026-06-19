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
@TableName("cost_summary")
public class CostSummary extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long projectId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate summaryDate;

    private Long costSubjectId;

    /** 关联 cost_target.id，用于目标成本版本追溯 */
    private Long costTargetId;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal targetCost;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal contractLockedCost;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal actualCost;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal paidAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal estimatedRemainingCost;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal dynamicCost;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal contractIncome;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal confirmedRevenue;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal expectedProfit;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal costDeviation;
}
