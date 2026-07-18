package com.cgcpms.inventory.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 当前库存项的历史净领料事实，不代表需求预测。 */
@Data
public class StockConsumptionBaselineVO {

    private LocalDate window30Start;
    private LocalDate window90Start;
    private LocalDateTime cutoffAt;
    private BigDecimal grossIssued30;
    private BigDecimal returned30;
    private BigDecimal netIssued30;
    private BigDecimal grossIssued90;
    private BigDecimal returned90;
    private BigDecimal netIssued90;
}
