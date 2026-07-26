package com.cgcpms.inventory.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal grossIssued30;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal returned30;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal netIssued30;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal grossIssued90;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal returned90;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal netIssued90;
}
