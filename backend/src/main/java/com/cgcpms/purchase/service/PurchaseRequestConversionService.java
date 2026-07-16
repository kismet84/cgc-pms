package com.cgcpms.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseRequestConversionService {

    private final MatPurchaseRequestMapper requestMapper;
    private final MatPurchaseRequestItemMapper requestItemMapper;
    private final MatPurchaseOrderMapper orderMapper;
    private final MatPurchaseOrderItemMapper orderItemMapper;

    public Long convertApprovedRequest(MatPurchaseRequest request) {
        Long requestId = request.getId();
        Long tenantId = request.getTenantId();

        List<MatPurchaseRequestItem> requestItems = requestItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseRequestItem>()
                        .eq(MatPurchaseRequestItem::getRequestId, requestId)
                        .eq(MatPurchaseRequestItem::getTenantId, tenantId));
        if (requestItems.isEmpty()) {
            throw new BusinessException("PURCHASE_REQUEST_NO_ITEMS", "采购申请没有明细，无法转换采购订单");
        }

        int marked = requestMapper.update(null, new LambdaUpdateWrapper<MatPurchaseRequest>()
                .eq(MatPurchaseRequest::getId, requestId)
                .eq(MatPurchaseRequest::getTenantId, tenantId)
                .eq(MatPurchaseRequest::getStatus, "APPROVED")
                .set(MatPurchaseRequest::getStatus, "CONVERTED"));
        if (marked == 0) {
            throw new BusinessException("REQUEST_ALREADY_CONVERTED", "采购申请已转换或状态已变化，不可重复转换");
        }

        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setTenantId(tenantId);
        order.setProjectId(request.getProjectId());
        order.setRequestId(requestId);
        order.setOrderCode(toPurchaseOrderCode(request.getRequestCode()));
        order.setOrderType("PURCHASE");
        order.setOrderDate(LocalDate.now());
        // 采购申请只批准内部需求，不代表供应商、价格和交付条件等商业承诺已批准。
        // 转单后必须由采购人员补齐商业条件并重新提交采购订单审批。
        order.setApprovalStatus("DRAFT");
        order.setOrderStatus("DRAFT");
        order.setContractId(request.getContractId());
        order.setTotalAmount(BigDecimal.ZERO);
        orderMapper.insert(order);

        Long userId = UserContext.getCurrentUserId();
        List<MatPurchaseOrderItem> orderItems = requestItems.stream()
                .map(item -> toOrderItem(item, order.getId(), request.getProjectId(), tenantId, userId))
                .toList();
        orderItemMapper.insertBatch(orderItems);

        log.info("采购申请转换采购订单完成 requestId={} -> orderId={} poCode={}",
                requestId, order.getId(), order.getOrderCode());
        return order.getId();
    }

    private String toPurchaseOrderCode(String requestCode) {
        if (requestCode != null && requestCode.startsWith("PR-")) {
            return "PO-" + requestCode.substring(3);
        }
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        return "PO-" + today + "-001";
    }

    private MatPurchaseOrderItem toOrderItem(MatPurchaseRequestItem requestItem, Long orderId,
                                             Long projectId, Long tenantId, Long userId) {
        MatPurchaseOrderItem orderItem = new MatPurchaseOrderItem();
        orderItem.setId(IdWorker.getId());
        orderItem.setTenantId(tenantId);
        orderItem.setOrderId(orderId);
        orderItem.setRequestItemId(requestItem.getId());
        orderItem.setProjectId(projectId);
        orderItem.setMaterialId(requestItem.getMaterialId());
        orderItem.setUnit(requestItem.getUnit());
        orderItem.setQuantity(requestItem.getQuantity());
        orderItem.setUnitPrice(BigDecimal.ZERO);
        orderItem.setAmount(BigDecimal.ZERO);
        orderItem.setReceivedQuantity(BigDecimal.ZERO);
        orderItem.setCreatedBy(userId);
        orderItem.setUpdatedBy(userId);
        return orderItem;
    }
}
