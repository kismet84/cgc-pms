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
    @Digits(integer = 16, fraction = 2)
    private BigDecimal acceptedQuantity;

    @Size(max = 200)
    private String useLocation;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored) {
        throw new IllegalArgumentException("验收明细不支持字段: " + field);
    }
}
