package com.cgcpms.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 仓库表实体 — 对应 V35 mat_warehouse 表。
 * <p>
 * V35 原使用 created_time / updated_time；V45 已统一为 created_at / updated_at。
 * 为兼容既有 Java 调用方保留 createdTime / updatedTime 属性并显式映射当前列。
 * <p>
 * 其他审计字段 (created_by, updated_by, deleted_flag, remark) 列名与 BaseEntity 默认
 * 驼峰→下划线映射一致，直接继承使用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_warehouse")
public class MatWarehouse extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    private Long projectId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String warehouseCode;

    private String warehouseName;

    /** 状态：ENABLE启用，DISABLE禁用 */
    private String status;

    // ── V35 原列经 V45 统一为 created_at / updated_at ──

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    /** 屏蔽 BaseEntity.createdAt，当前列由 createdTime 显式映射。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(exist = false)
    private LocalDateTime createdAt;

    /** 屏蔽 BaseEntity.updatedAt，当前列由 updatedTime 显式映射。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}
