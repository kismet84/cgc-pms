package com.cgcpms.inventory.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 同项目其他仓库可调拨余量的只读快照。 */
@Data
public class StockTransferCandidateVO implements Serializable {

    private Long stockId;
    private Long warehouseId;
    private String warehouseName;
    private BigDecimal availableQty;
    private BigDecimal safetyStockQty;
    private BigDecimal transferableQty;
}
