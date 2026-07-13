package com.cgcpms.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.math.BigDecimal;

@Data
public class ReplenishmentSettingsDTO {

    @NotNull(message = "安全库存阈值不能为空")
    @DecimalMin(value = "0.0000", message = "安全库存阈值不能为负数")
    @Digits(integer = 14, fraction = 4, message = "安全库存阈值最多 14 位整数和 4 位小数")
    private BigDecimal safetyStockQty;

    @DecimalMin(value = "0.0000", message = "人工补货目标量不能为负数")
    @Digits(integer = 14, fraction = 4, message = "人工补货目标量最多 14 位整数和 4 位小数")
    private BigDecimal replenishmentTargetQty;

    @DecimalMin(value = "0", message = "人工补货提前期不能为负数")
    @DecimalMax(value = "3650", message = "人工补货提前期不能超过 3650 天")
    @Digits(integer = 4, fraction = 0, message = "人工补货提前期必须为整数")
    private BigDecimal replenishmentLeadDays;

    @JsonIgnore
    private boolean replenishmentLeadDaysSpecified;

    @JsonSetter("replenishmentLeadDays")
    public void setReplenishmentLeadDays(BigDecimal replenishmentLeadDays) {
        this.replenishmentLeadDays = replenishmentLeadDays;
        this.replenishmentLeadDaysSpecified = true;
    }
}
