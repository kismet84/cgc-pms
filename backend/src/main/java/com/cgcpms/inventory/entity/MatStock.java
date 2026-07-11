package com.cgcpms.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存余额表实体 — 对应 V35 mat_stock 表。
 * <p>
 * V35 使用 created_time / updated_time 列名，与 BaseEntity 默认的 created_at / updated_at
 * 不同。因此新增 createdTime / updatedTime 字段并显式映射，同时将 BaseEntity 的
 * createdAt / updatedAt 标记为 exist=false 以避免映射冲突。
 * <p>
 * version 字段使用 MyBatis-Plus @Version 注解实现乐观锁并发控制。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_stock")
public class MatStock extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long warehouseId;

    private Long materialId;

    /** 可用数量，精度18,4 */
    private BigDecimal availableQty;

    /** 安全库存阈值，按租户内仓库+物料库存项维护 */
    private BigDecimal safetyStockQty;

    /** 人工补货目标量；NULL 表示回退到安全库存阈值 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal replenishmentTargetQty;

    /** 乐观锁版本号，MyBatis-Plus @Version 自动在 update 时递增并作为 WHERE 条件 */
    @Version
    private Integer version;

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
