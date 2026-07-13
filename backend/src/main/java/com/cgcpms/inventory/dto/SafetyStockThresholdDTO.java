package com.cgcpms.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SafetyStockThresholdDTO {

    @NotNull(message = "安全库存阈值不能为空")
    @DecimalMin(value = "0.0000", message = "安全库存阈值不能为负数")
    @Digits(integer = 14, fraction = 4, message = "安全库存阈值最多 14 位整数和 4 位小数")
    private BigDecimal safetyStockQty;
}
