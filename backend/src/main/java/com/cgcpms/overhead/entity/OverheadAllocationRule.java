package com.cgcpms.overhead.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 间接费用分摊规则实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("overhead_allocation_rule")
public class OverheadAllocationRule extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 间接费用科目ID（5401.04.xx） */
    private Long costSubjectId;

    /** DIRECT_LABOR / CONTRACT_AMOUNT / USAGE */
    private String allocationBasis;

    /** MONTHLY / PER_OCCURRENCE */
    private String allocationCycle;

    private String status;
}
