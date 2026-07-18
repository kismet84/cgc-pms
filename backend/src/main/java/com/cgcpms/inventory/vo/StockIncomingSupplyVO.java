package com.cgcpms.inventory.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 已审批采购订单尚未收货数量的只读快照。 */
@Data
public class StockIncomingSupplyVO implements Serializable {

    private Long orderId;
    private String orderCode;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    private BigDecimal remainingQty;
}
