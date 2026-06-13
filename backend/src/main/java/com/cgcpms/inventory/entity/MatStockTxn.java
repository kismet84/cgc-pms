package com.cgcpms.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水表实体 — 对应 V35 mat_stock_txn 表。
 * <p>
 * 记录每一次入库/出库/调整操作的流水账。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_stock_txn")
public class MatStockTxn extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long warehouseId;

    private Long materialId;

    /** 交易类型：IN 入库，OUT 出库，ADJUST 调整 */
    private String txnType;

    /** 交易数量（入库为正，出库为正数由服务层控制） */
    private BigDecimal quantity;

    /** 交易后可用量快照 */
    private BigDecimal availableAfter;

    /** 来源业务类型 */
    private String sourceType;

    /** 来源业务ID */
    private Long sourceId;

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
