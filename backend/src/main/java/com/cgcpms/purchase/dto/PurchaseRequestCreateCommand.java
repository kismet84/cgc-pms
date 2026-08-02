package com.cgcpms.purchase.dto;

import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 创建采购申请：申请头与全部明细由同一服务端事务保存。 */
public record PurchaseRequestCreateCommand(
        @NotNull @Valid MatPurchaseRequest header,
        @NotEmpty @Size(max = 200) List<MatPurchaseRequestItem> items) {
}
