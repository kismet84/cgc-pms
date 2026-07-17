package com.cgcpms.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseRequestConversionService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;

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
        order.setOrderType("PURCHASE");
        order.setOrderDate(LocalDate.now());
        // 采购申请只批准内部需求，不代表供应商、价格和交付条件等商业承诺已批准。
        // 转单后必须由采购人员补齐商业条件并重新提交采购订单审批。
        order.setApprovalStatus("DRAFT");
        order.setOrderStatus("DRAFT");
        order.setContractId(request.getContractId());
        order.setTotalAmount(BigDecimal.ZERO);
        String prefix = "PO-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
        boolean inserted = false;
        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            order.setOrderCode(nextOrderCode(prefix, tenantId, attempt));
            try {
                orderMapper.insert(order);
                inserted = true;
                break;
            } catch (DuplicateKeyException exception) {
                log.warn("采购申请转单编号冲突，重试生成 orderCode={}", order.getOrderCode());
            }
        }
        if (!inserted) {
            throw new BusinessException("PURCHASE_ORDER_CODE_CONFLICT", "采购订单编号生成冲突，请重试");
        }

        Long userId = UserContext.getCurrentUserId();
        List<MatPurchaseOrderItem> orderItems = requestItems.stream()
                .map(item -> toOrderItem(item, order.getId(), request.getProjectId(), tenantId, userId))
                .toList();
        orderItemMapper.insertBatch(orderItems);

        log.info("采购申请转换采购订单完成 requestId={} -> orderId={} poCode={}",
                requestId, order.getId(), order.getOrderCode());
        return order.getId();
    }

    private String nextOrderCode(String prefix, Long tenantId, int offset) {
        Page<MatPurchaseOrder> page = orderMapper.selectPage(new Page<>(1, 1),
                new LambdaQueryWrapper<MatPurchaseOrder>()
                        .eq(MatPurchaseOrder::getTenantId, tenantId)
                        .likeRight(MatPurchaseOrder::getOrderCode, prefix)
                        .orderByDesc(MatPurchaseOrder::getOrderCode));
        MatPurchaseOrder last = page.getRecords().isEmpty() ? null : page.getRecords().getFirst();
        int sequence = 1 + offset;
        if (last != null && last.getOrderCode() != null && last.getOrderCode().startsWith(prefix)) {
            try {
                sequence = Integer.parseInt(last.getOrderCode().substring(prefix.length())) + 1 + offset;
            } catch (NumberFormatException exception) {
                log.warn("采购订单编号后缀无法解析，改用候选序号：{}", last.getOrderCode());
            }
        }
        return prefix + String.format("%03d", sequence);
    }

    private MatPurchaseOrderItem toOrderItem(MatPurchaseRequestItem requestItem, Long orderId,
                                             Long projectId, Long tenantId, Long userId) {
        MatPurchaseOrderItem orderItem = new MatPurchaseOrderItem();
        orderItem.setId(IdWorker.getId());
        orderItem.setTenantId(tenantId);
        orderItem.setOrderId(orderId);
        orderItem.setRequestItemId(requestItem.getId());
        orderItem.setWbsTaskId(requestItem.getWbsTaskId());
        orderItem.setBudgetLineId(requestItem.getBudgetLineId());
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
