package com.cgcpms.cost.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_subject")
public class CostSubject extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long parentId;

    private String subjectCode;

    private String subjectName;

    private String subjectType;

    /** 科目大类：ASSET资产 / LIABILITY负债 / EQUITY权益 / COST成本 / REVENUE收入 / SETTLEMENT结算 / RECEIVABLE应收 */
    private String accountCategory;

    private Integer level;

    private Integer sortOrder;

    private String status;

    private BigDecimal defaultTargetRatio;

    /** 1=固定总账会计科目；0=项目成本/业务分类。 */
    private Integer ledgerFlag;

    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.ALWAYS)
    private Long createdBy;

    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.ALWAYS)
    private Long updatedBy;

    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
