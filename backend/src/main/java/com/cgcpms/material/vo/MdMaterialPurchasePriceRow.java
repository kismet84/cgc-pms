package com.cgcpms.material.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MdMaterialPurchasePriceRow {
    private Long materialId;
    private BigDecimal purchasePrice;
    private Long receiptItemId;
    private LocalDate receiptDate;
}
