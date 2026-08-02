package com.cgcpms.purchase.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** 从已审批采购申请显式创建采购订单；商业价格由服务端合同事实决定。 */
public record PurchaseOrderFromRequestCommand(
        @NotNull Long projectId,
        @NotNull Long contractId,
        @NotNull Long requestId,
        LocalDate orderDate,
        LocalDate deliveryDate,
        String deliveryTerms,
        String remark) {
}
