package com.cgcpms.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 仓库表实体 — 对应 V35 mat_warehouse 表。
 * <p>
 * V35 使用 created_time / updated_time 列名，与 BaseEntity 默认的 created_at / updated_at
 * 不同。因此新增 createdTime / updatedTime 字段并显式映射，同时将 BaseEntity 的
 * createdAt / updatedAt 标记为 exist=false 以避免映射冲突。
 * <p>
 * 其他审计字段 (created_by, updated_by, deleted_flag, remark) 列名与 BaseEntity 默认
 * 驼峰→下划线映射一致，直接继承使用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_warehouse")
public class MatWarehouse extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long projectId;

    private String warehouseCode;

    private String warehouseName;

    /** 状态：ENABLE启用，DISABLE禁用 */
    private String status;

    // ── V35 使用 created_time / updated_time 列名 ──

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    /** 屏蔽 BaseEntity.createdAt（V35 表无 created_at 列） */
    @TableField(exist = false)
    private LocalDateTime createdAt;

    /** 屏蔽 BaseEntity.updatedAt（V35 表无 updated_at 列） */
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}
