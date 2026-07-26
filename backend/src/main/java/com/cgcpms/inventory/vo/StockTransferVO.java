package com.cgcpms.inventory.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal quantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal unitCost;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amount;
    private String idempotencyKey;
    private String status;
    private String reason;
    private LocalDateTime completedAt;
}
