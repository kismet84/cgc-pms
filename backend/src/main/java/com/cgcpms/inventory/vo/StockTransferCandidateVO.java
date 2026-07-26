package com.cgcpms.inventory.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 同项目其他仓库可调拨余量的只读快照。 */
@Data
public class StockTransferCandidateVO implements Serializable {

    private Long stockId;
    private Long warehouseId;
    private String warehouseName;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal availableQty;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal safetyStockQty;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal transferableQty;
}
