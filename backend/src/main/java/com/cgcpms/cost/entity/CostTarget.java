package com.cgcpms.cost.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 目标成本表实体 — 对应 V22 cost_target 表。
 * <p>
 * V22 原使用 created_time / updated_time 列名，经 V44 统一重命名为 created_at / updated_at。
 * 保留显式映射以确保向后兼容，同时将 BaseEntity 的 createdAt / updatedAt 标记为 exist=false
 * 以避免映射冲突。
 * <p>
 * 其他审计字段 (created_by, updated_by, deleted_flag, remark) 列名与 BaseEntity 默认
 * 驼峰→下划线映射一致，直接继承使用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_target")
public class CostTarget extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long projectId;

    private String versionNo;

    private String versionName;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalTargetAmount;

    /** 是否生效版本：0否，1是。同一项目仅允许一个生效版本 */
    private Integer isActive;

    /** 审批状态：DRAFT草稿，APPROVING审批中，APPROVED已通过，REJECTED已驳回 */
    private String approvalStatus;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;

    /** 业务状态：DRAFT草稿，ACTIVE已生效，CANCELLED已作废 */
    private String status;

    // ── V22 列经 V44 统一为 created_at / updated_at ──

    @TableField("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableField("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    /** 屏蔽 BaseEntity.createdAt（V22 表无 created_at 列） */
    @TableField(exist = false)
    private LocalDateTime createdAt;

    /** 屏蔽 BaseEntity.updatedAt（V22 表无 updated_at 列） */
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}
