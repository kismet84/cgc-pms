package com.cgcpms.receipt.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 验收明细写模型：其余数量、价格、金额及状态均由服务端派生。 */
@Data
public class MatReceiptItemCommand {
    @NotNull
    private Long orderItemId;

    @NotNull
    @DecimalMin(value = "0.0001")
    // mat_receipt_item.qualified_quantity 是 decimal(18,4)：整数位 14、小数位 4。
    // 原来的 (16, 2) 既放过数据库会截断的 16 位整数，又让 @DecimalMin("0.0001") 永远无法满足。
    @Digits(integer = 14, fraction = 4)
    private BigDecimal acceptedQuantity;

    @Size(max = 200)
    private String useLocation;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored) {
        throw new IllegalArgumentException("验收明细不支持字段: " + field);
    }
}
