package com.cgcpms.receipt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PurchaseOrderReceiptStateService {
    private final MatPurchaseOrderMapper orderMapper;
    private final MatPurchaseOrderItemMapper orderItemMapper;

    public void sync(Long orderId, Long tenantId) {
        MatPurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null || !Objects.equals(tenantId, order.getTenantId())) {
            throw new BusinessException("ORDER_NOT_FOUND", "关联采购订单不存在");
        }
        List<MatPurchaseOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getOrderId, orderId)
                        .eq(MatPurchaseOrderItem::getTenantId, tenantId));
        boolean completed = !items.isEmpty() && items.stream().allMatch(item ->
                nvl(item.getReceivedQuantity()).compareTo(nvl(item.getQuantity())) >= 0);
        boolean received = items.stream().anyMatch(item -> nvl(item.getReceivedQuantity()).signum() > 0);
        String status = completed ? "COMPLETED" : received ? "PARTIAL_RECEIVED" : "PERFORMING";
        orderMapper.update(null, new LambdaUpdateWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getId, orderId)
                .eq(MatPurchaseOrder::getTenantId, tenantId)
                .eq(MatPurchaseOrder::getApprovalStatus, "APPROVED")
                .in(MatPurchaseOrder::getOrderStatus, "PERFORMING", "PARTIAL_RECEIVED", "COMPLETED")
                .set(MatPurchaseOrder::getOrderStatus, status));
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
