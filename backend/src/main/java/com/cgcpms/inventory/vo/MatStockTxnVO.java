package com.cgcpms.inventory.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 库存流水 VO — 不暴露租户ID等内部字段，包含仓库/物料 display name。
 */
@Data
public class MatStockTxnVO implements Serializable {

    private Long id;
    private Long warehouseId;
    private Long materialId;
    private String txnType;
    private BigDecimal quantity;
    private BigDecimal availableAfter;
    private BigDecimal unitCost;
    private BigDecimal amount;
    private String sourceType;
    private Long sourceId;
    private Long sourceLineId;
    private String createdTime;

    /** 仓库名称（JOIN 填充） */
    private String warehouseName;

    /** 物料名称（JOIN 填充） */
    private String materialName;
}
