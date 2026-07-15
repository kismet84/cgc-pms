package com.cgcpms.overhead.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 租户、规则、自然月维度的间接费执行事实。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("overhead_allocation_run")
public class OverheadAllocationRun extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long ruleId;
    private LocalDate period;
    private String triggerType;
    private Long executedBy;
    private String runStatus;
    private BigDecimal allocatedAmount;
    private Integer costItemCount;
}
