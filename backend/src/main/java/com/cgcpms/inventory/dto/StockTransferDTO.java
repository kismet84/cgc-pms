package com.cgcpms.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 同项目跨仓库存调拨请求。 */
@Data
public class StockTransferDTO {

    @NotNull(message = "来源库存ID不能为空")
    private Long sourceStockId;

    @NotNull(message = "目标库存ID不能为空")
    private Long targetStockId;

    @NotNull(message = "调拨数量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "调拨数量必须大于0")
    @Digits(integer = 14, fraction = 4, message = "调拨数量最多14位整数和4位小数")
    private BigDecimal quantity;

    @NotBlank(message = "幂等键不能为空")
    @Size(max = 100, message = "幂等键不能超过100个字符")
    private String idempotencyKey;

    @NotBlank(message = "调拨原因不能为空")
    @Size(max = 500, message = "调拨原因不能超过500个字符")
    private String reason;
}
