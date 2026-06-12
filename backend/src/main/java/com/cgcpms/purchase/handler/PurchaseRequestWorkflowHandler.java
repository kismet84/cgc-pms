package com.cgcpms.purchase.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Business handler for purchase request approval workflows.
 * On approval: updates status to APPROVED, then converts to purchase order.
 * Critical handler: callback failures roll back the entire approval transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseRequestWorkflowHandler implements WorkflowBusinessHandler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MatPurchaseRequestMapper requestMapper;
    private final MatPurchaseRequestItemMapper requestItemMapper;
    private final MatPurchaseOrderMapper orderMapper;
    private final MatPurchaseOrderItemMapper orderItemMapper;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.PURCHASE_REQUEST;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    @Transactional
    public void onApproved(WorkflowContext context) {
        Long requestId = resolveRequestId(context.getInstance());
        log.info("采购申请审批通过，开始处理 requestId={}", requestId);

        MatPurchaseRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException("REQUEST_NOT_FOUND", "采购申请不存在");
        }

        // Update approval status to APPROVED and status to APPROVED
        requestMapper.update(null, new LambdaUpdateWrapper<MatPurchaseRequest>()
                .eq(MatPurchaseRequest::getId, requestId)
                .set(MatPurchaseRequest::getApprovalStatus, "APPROVED")
                .set(MatPurchaseRequest::getStatus, "APPROVED"));

        // Convert to purchase order
        convertToPurchaseOrder(request);
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long requestId = resolveRequestId(context.getInstance());
        log.info("采购申请审批驳回 requestId={}", requestId);

        requestMapper.update(null, new LambdaUpdateWrapper<MatPurchaseRequest>()
                .eq(MatPurchaseRequest::getId, requestId)
                .set(MatPurchaseRequest::getApprovalStatus, "REJECTED"));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long requestId = resolveRequestId(context.getInstance());
        log.info("采购申请审批撤回，恢复为草稿 requestId={}", requestId);

        requestMapper.update(null, new LambdaUpdateWrapper<MatPurchaseRequest>()
                .eq(MatPurchaseRequest::getId, requestId)
                .set(MatPurchaseRequest::getApprovalStatus, "DRAFT"));
    }

    private Long resolveRequestId(WfInstance instance) {
        Long requestId = instance.getBusinessId();
        if (requestId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（采购申请ID），instanceId=" + instance.getId());
        }
        return requestId;
    }

    /**
     * Convert approved purchase request to a purchase order.
     * Generates PO code by replacing PR- prefix with PO- prefix,
     * copies request items to purchase order items.
     */
    private void convertToPurchaseOrder(MatPurchaseRequest request) {
        Long requestId = request.getId();
        Long tenantId = request.getTenantId();

        // Check if already converted
        if ("CONVERTED".equals(request.getStatus())) {
            log.info("采购申请已转换，跳过 requestId={}", requestId);
            return;
        }

        // Load request items
        List<MatPurchaseRequestItem> requestItems = requestItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatPurchaseRequestItem>()
                        .eq(MatPurchaseRequestItem::getRequestId, requestId)
                        .eq(MatPurchaseRequestItem::getTenantId, tenantId));

        // Generate PO code: replace PR- prefix with PO-
        String poCode = request.getRequestCode();
        if (poCode != null && poCode.startsWith("PR-")) {
            poCode = "PO-" + poCode.substring(3);
        }

        // Auto-generate PO code with date sequence if needed
        if (poCode == null || poCode.isBlank()) {
            String today = LocalDate.now().format(DATE_FMT);
            poCode = "PO-" + today + "-001";
        }

        // Create purchase order
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setTenantId(tenantId);
        order.setProjectId(request.getProjectId());
        order.setRequestId(requestId);
        order.setOrderCode(poCode);
        order.setOrderType("PURCHASE");
        order.setOrderDate(LocalDate.now());
        order.setApprovalStatus("APPROVED");
        order.setOrderStatus("APPROVED");

        // Calculate total amount from items
        BigDecimal totalAmount = requestItems.stream()
                .map(MatPurchaseRequestItem::getQuantity)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);

        orderMapper.insert(order);

        // Create purchase order items from request items
        for (MatPurchaseRequestItem reqItem : requestItems) {
            MatPurchaseOrderItem orderItem = new MatPurchaseOrderItem();
            orderItem.setTenantId(tenantId);
            orderItem.setOrderId(order.getId());
            orderItem.setProjectId(request.getProjectId());
            orderItem.setMaterialId(reqItem.getMaterialId());
            orderItem.setUnit(reqItem.getUnit());
            orderItem.setQuantity(reqItem.getQuantity());
            orderItem.setReceivedQuantity(BigDecimal.ZERO);
            orderItemMapper.insert(orderItem);
        }

        // Mark request as CONVERTED
        requestMapper.update(null, new LambdaUpdateWrapper<MatPurchaseRequest>()
                .eq(MatPurchaseRequest::getId, requestId)
                .set(MatPurchaseRequest::getStatus, "CONVERTED"));

        log.info("采购申请转换采购订单完成 requestId={} -> orderId={} poCode={}",
                requestId, order.getId(), poCode);
    }
}
