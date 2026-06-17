package com.cgcpms.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 入库/出库请求 DTO。
 * <p>
 * 前端发送 JSON body，由 {@code @Valid @RequestBody} 绑定并校验。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockTransactionDTO {

    @NotNull(message = "仓库ID不能为空")
    private Long warehouseId;

    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    @NotNull(message = "数量不能为空")
    @Positive(message = "数量必须大于0")
    private BigDecimal quantity;
}
