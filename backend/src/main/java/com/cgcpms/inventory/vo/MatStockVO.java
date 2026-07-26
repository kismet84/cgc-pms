package com.cgcpms.inventory.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    private Long projectId;
    private Long materialId;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal availableQty;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal inventoryValue;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal averageUnitCost;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal safetyStockQty;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal replenishmentTargetQty;
    private Integer replenishmentLeadDays;
    private String createdTime;
    private String updatedTime;

    /** 仓库名称（JOIN 填充） */
    private String warehouseName;

    /** 项目名称（经当前用户项目范围填充） */
    private String projectName;

    /** 物料名称（JOIN 填充） */
    private String materialName;

    /** 物料编码（JOIN 填充） */
    private String materialCode;

    /** 物料单位（JOIN 填充） */
    private String unit;
}
