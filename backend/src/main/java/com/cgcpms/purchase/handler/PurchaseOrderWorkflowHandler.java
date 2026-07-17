package com.cgcpms.purchase.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.budget.service.BudgetLedgerService;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business handler for purchase order approval workflows.
 * Critical handler: callback failures roll back the entire approval transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOrderWorkflowHandler implements WorkflowBusinessHandler {

    private final MatPurchaseOrderMapper orderMapper;
    private final CtContractMapper contractMapper;
    private final MatPurchaseOrderItemMapper orderItemMapper;
    private final MatPurchaseRequestItemMapper requestItemMapper;
    private final BudgetLedgerService budgetLedgerService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.PURCHASE_ORDER;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(WorkflowContext context) {
        Long orderId = resolveOrderId(context.getInstance());
        log.info("采购订单审批通过，更新状态 orderId={}", orderId);

        MatPurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "采购订单不存在");
        }
        if ("APPROVED".equals(order.getApprovalStatus())) {
            return;
        }

        // Validate contract balance before approval
        // Uses SELECT ... FOR UPDATE to prevent TOCTOU race with concurrent approvals
        if (order.getContractId() != null) {
            CtContract contract = contractMapper.selectByIdForUpdate(order.getContractId(), order.getTenantId());
            if (contract != null) {
                BigDecimal contractBalance = contract.getCurrentAmount() != null
                        ? contract.getCurrentAmount() : BigDecimal.ZERO;

                List<MatPurchaseOrder> approvedOrders = orderMapper.selectList(
                        new LambdaQueryWrapper<MatPurchaseOrder>()
                                .eq(MatPurchaseOrder::getContractId, order.getContractId())
                                .eq(MatPurchaseOrder::getOrderStatus, "APPROVED")
                                .ne(MatPurchaseOrder::getId, order.getId()));
                BigDecimal totalApproved = approvedOrders.stream()
                        .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal orderAmount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal newTotal = totalApproved.add(orderAmount);
                if (newTotal.compareTo(contractBalance) > 0) {
                    throw new BusinessException("ORDER_EXCEED_CONTRACT",
                            "采购订单总额(" + newTotal + ")超过合同可用余额(" + contractBalance + ")");
                }
            }
        }

        consumeBudget(order);
        orderMapper.update(null, new LambdaUpdateWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getId, orderId)
                .set(MatPurchaseOrder::getApprovalStatus, "APPROVED")
                .set(MatPurchaseOrder::getOrderStatus, "APPROVED"));
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long orderId = resolveOrderId(context.getInstance());
        log.info("采购订单审批驳回 orderId={}", orderId);

        orderMapper.update(null, new LambdaUpdateWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getId, orderId)
                .set(MatPurchaseOrder::getApprovalStatus, "REJECTED"));
        releaseExceptionBudget(orderId, "REJECT");
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long orderId = resolveOrderId(context.getInstance());
        log.info("采购订单审批撤回，恢复为草稿 orderId={}", orderId);

        orderMapper.update(null, new LambdaUpdateWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getId, orderId)
                .set(MatPurchaseOrder::getApprovalStatus, "DRAFT"));
        releaseExceptionBudget(orderId, "WITHDRAW");
    }

    private void consumeBudget(MatPurchaseOrder order) {
        List<MatPurchaseOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getTenantId, order.getTenantId())
                        .eq(MatPurchaseOrderItem::getOrderId, order.getId()));
        for (MatPurchaseOrderItem item : items) {
            budgetLedgerService.consume(item.getBudgetLineId(), "PURCHASE_ORDER", order.getId(), item.getAmount(),
                    "PURCHASE_ORDER:" + order.getId() + ":ITEM:" + item.getId() + ":CONSUME");
            if (item.getRequestItemId() != null) {
                MatPurchaseRequestItem requestItem = requestItemMapper.selectById(item.getRequestItemId());
                BigDecimal remaining = requestItem.getEstimatedAmount().subtract(item.getAmount());
                if (remaining.signum() > 0) {
                    budgetLedgerService.release(item.getBudgetLineId(), "PURCHASE_REQUEST", order.getRequestId(), remaining,
                            "PURCHASE_ORDER:" + order.getId() + ":ITEM:" + item.getId() + ":RELEASE_VARIANCE");
                }
            }
        }
    }

    private void releaseExceptionBudget(Long orderId, String action) {
        MatPurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getRequestId() != null) return;
        orderItemMapper.selectList(new LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getTenantId, order.getTenantId())
                        .eq(MatPurchaseOrderItem::getOrderId, orderId))
                .forEach(item -> budgetLedgerService.release(item.getBudgetLineId(), "PURCHASE_ORDER", orderId,
                        item.getAmount(), "PURCHASE_ORDER:" + orderId + ":ITEM:" + item.getId()
                                + ":" + action + ":RELEASE"));
    }

    private Long resolveOrderId(WfInstance instance) {
        Long orderId = instance.getBusinessId();
        if (orderId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（订单ID），instanceId=" + instance.getId());
        }
        return orderId;
    }
}
