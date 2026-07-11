package com.cgcpms.inventory.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 库存余额 VO — 不暴露 version 等内部字段，包含仓库/物料 display name。
 */
@Data
public class MatStockVO implements Serializable {

    private Long id;
    private Long warehouseId;
    private Long materialId;
    private BigDecimal availableQty;
    private BigDecimal safetyStockQty;
    private BigDecimal replenishmentTargetQty;
    private Integer replenishmentLeadDays;
    private String createdTime;
    private String updatedTime;

    /** 仓库名称（JOIN 填充） */
    private String warehouseName;

    /** 物料名称（JOIN 填充） */
    private String materialName;

    /** 物料编码（JOIN 填充） */
    private String materialCode;

    /** 物料单位（JOIN 填充） */
    private String unit;
}
