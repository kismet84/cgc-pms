package com.cgcpms.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 同项目跨仓库存调拨事实。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_stock_transfer")
public class MatStockTransfer extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long sourceStockId;
    private Long targetStockId;
    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private Long materialId;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal amount;
    private String idempotencyKey;
    private String status;
    private LocalDateTime completedAt;
}
