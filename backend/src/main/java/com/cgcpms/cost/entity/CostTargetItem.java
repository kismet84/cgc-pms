package com.cgcpms.cost.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 目标成本明细表实体 — 对应 V22 cost_target_item 表。
 * <p>
 * V22 原审计时间列已统一为 created_at / updated_at；保留旧 Java 属性名兼容调用方。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_target_item")
public class CostTargetItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    /** 关联 cost_target.id */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    /** 关联 cost_subject.id */
    @JsonSerialize(using = ToStringSerializer.class)
    @NotNull(message = "成本科目不能为空")
    private Long costSubjectId;

    @JsonSerialize(using = ToStringSerializer.class)
    @NotNull(message = "目标金额不能为空")
    @DecimalMin(value = "0.00", message = "目标金额不能为负数")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal targetAmount;

    /** 投标阶段该科目成本基准快照。 */
    @DecimalMin(value = "0.00", message = "投标成本金额不能为负数")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal bidCostAmount;

    /** 下达给责任主体的预算金额。 */
    @DecimalMin(value = "0.00", message = "责任预算金额不能为负数")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal responsibilityAmount;

    private Long responsibleUserId;

    private String responsibilityUnit;

    private Integer sortOrder;

    // ── V22 原列经迁移统一为 created_at / updated_at ──

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(exist = false)
    private LocalDateTime createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}
