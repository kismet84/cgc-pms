package com.cgcpms.purchase.vo;

public record PurchaseOrderPricingSuggestionVO(
        String pricingMode,
        String contractItemId,
        String unitPrice,
        boolean editable,
        String priceSource,
        String sourceReceiptItemId,
        String sourceReceiptDate) {
}
