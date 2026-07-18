package com.cgcpms.inventory.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 已完成库存调拨结果。 */
@Data
public class StockTransferVO implements Serializable {
    private Long id;
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
    private String reason;
    private LocalDateTime completedAt;
}
