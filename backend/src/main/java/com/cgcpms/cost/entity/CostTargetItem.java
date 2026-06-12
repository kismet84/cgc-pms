package com.cgcpms.cost.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 目标成本明细表实体 — 对应 V22 cost_target_item 表。
 * <p>
 * 同样使用 V22 的 created_time / updated_time 列名约定。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_target_item")
public class CostTargetItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 关联 cost_target.id */
    private Long targetId;

    private Long projectId;

    /** 关联 cost_subject.id */
    private Long costSubjectId;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal targetAmount;

    // ── V22 使用 created_time / updated_time ──

    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableField("updated_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    @TableField(exist = false)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private LocalDateTime updatedAt;
}
