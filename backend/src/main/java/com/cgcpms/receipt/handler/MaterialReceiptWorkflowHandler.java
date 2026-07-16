package com.cgcpms.receipt.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
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
import java.util.Objects;

/**
 * Business handler for material receipt approval workflows.
 * On approval, auto-generates material cost records AND stock-in inventory.
 * Critical handler: cost generation or stock-in failure rolls back the entire approval transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialReceiptWorkflowHandler implements WorkflowBusinessHandler {

    private final MatReceiptMapper receiptMapper;
    private final MatReceiptItemMapper receiptItemMapper;
    private final MatPurchaseOrderMapper purchaseOrderMapper;
    private final MatPurchaseOrderItemMapper purchaseOrderItemMapper;
    private final MatWarehouseMapper warehouseMapper;
    private final CostGenerationService costGenerationService;
    private final MatStockService matStockService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.MATERIAL_RECEIPT;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(WorkflowContext context) {
        Long receiptId = resolveReceiptId(context.getInstance());
        log.info("材料验收审批通过，更新状态、自动入库并生成成本 receiptId={}", receiptId);

        MatReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null) {
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");
        }
        if ("APPROVED".equals(receipt.getApprovalStatus())) {
            log.info("材料验收已处理，忽略重复审批回调 receiptId={}", receiptId);
            return;
        }
        if (!"APPROVING".equals(receipt.getApprovalStatus())) {
            throw new BusinessException("RECEIPT_STATUS_INVALID", "验收单当前状态不允许确认入库");
        }

        MatPurchaseOrder order = validateReceiptRelations(receipt);
        List<MatReceiptItem> items = receiptItemMapper.selectList(
                new LambdaQueryWrapper<MatReceiptItem>()
                        .eq(MatReceiptItem::getReceiptId, receiptId)
                        .eq(MatReceiptItem::getTenantId, receipt.getTenantId()));
        if (items.isEmpty()) {
            throw new BusinessException("RECEIPT_ITEMS_REQUIRED", "验收单没有验收明细");
        }

        // 先以状态条件抢占本次业务事件；后续任一步失败都会随事务回滚。
        int claimed = receiptMapper.update(null, new LambdaUpdateWrapper<MatReceipt>()
                .eq(MatReceipt::getId, receiptId)
                .eq(MatReceipt::getApprovalStatus, "APPROVING")
                .set(MatReceipt::getApprovalStatus, "APPROVED"));
        if (claimed != 1) {
            MatReceipt latest = receiptMapper.selectById(receiptId);
            if (latest != null && "APPROVED".equals(latest.getApprovalStatus())) {
                return;
            }
            throw new BusinessException("RECEIPT_CONCURRENT_CONFLICT", "验收单审批状态已变化，请刷新后重试");
        }

        // 合格数量在审批通过时一次性确认：累计订单已验收量、入库、库存流水同事务完成。
        for (MatReceiptItem item : items) {
            MatPurchaseOrderItem orderItem = validateOrderItem(receipt, item);
            BigDecimal qualified = nvl(item.getQualifiedQuantity());
            if (qualified.signum() <= 0) {
                continue;
            }
            confirmReceivedQuantity(orderItem.getId(), qualified, receipt.getTenantId());
            if (!isDirectConsumption(receipt)) {
                log.info("验收自动入库 receiptId={} receiptItemId={} warehouseId={} materialId={} qty={}",
                        receiptId, item.getId(), receipt.getWarehouseId(), item.getMaterialId(), qualified);
                matStockService.stockInValued(receipt.getWarehouseId(), item.getMaterialId(), qualified,
                        nvl(item.getUnitPrice()), "MAT_RECEIPT", receiptId, item.getId());
            }
        }

        updateOrderCompletion(order);
        if (isDirectConsumption(receipt)) {
            costGenerationService.generateCost("MAT_RECEIPT", receiptId);
        }
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long receiptId = resolveReceiptId(context.getInstance());
        log.info("材料验收审批驳回 receiptId={}", receiptId);

        receiptMapper.update(null, new LambdaUpdateWrapper<MatReceipt>()
                .eq(MatReceipt::getId, receiptId)
                .set(MatReceipt::getApprovalStatus, "REJECTED"));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long receiptId = resolveReceiptId(context.getInstance());
        log.info("材料验收审批撤回，恢复为草稿 receiptId={}", receiptId);

        receiptMapper.update(null, new LambdaUpdateWrapper<MatReceipt>()
                .eq(MatReceipt::getId, receiptId)
                .set(MatReceipt::getApprovalStatus, "DRAFT"));
    }

    private Long resolveReceiptId(WfInstance instance) {
        Long receiptId = instance.getBusinessId();
        if (receiptId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（验收单ID），instanceId=" + instance.getId());
        }
        return receiptId;
    }

    private MatPurchaseOrder validateReceiptRelations(MatReceipt receipt) {
        if (receipt.getOrderId() == null) {
            throw new BusinessException("RECEIPT_RELATION_REQUIRED", "验收单缺少采购订单");
        }
        MatPurchaseOrder order = purchaseOrderMapper.selectById(receipt.getOrderId());
        if (order == null || !Objects.equals(receipt.getTenantId(), order.getTenantId())) {
            throw new BusinessException("ORDER_NOT_FOUND", "关联采购订单不存在");
        }
        if (!Objects.equals(receipt.getProjectId(), order.getProjectId())
                || !Objects.equals(receipt.getContractId(), order.getContractId())
                || !Objects.equals(receipt.getPartnerId(), order.getPartnerId())) {
            throw new BusinessException("RECEIPT_ORDER_MISMATCH", "验收单与采购订单的项目、合同或供应商不一致");
        }
        if (!"APPROVED".equals(order.getApprovalStatus()) || !"APPROVED".equals(order.getOrderStatus())) {
            throw new BusinessException("ORDER_NOT_APPROVED", "采购订单未审批通过或已结束");
        }
        if (isDirectConsumption(receipt)) {
            if (receipt.getWarehouseId() != null) {
                throw new BusinessException("DIRECT_RECEIPT_WAREHOUSE_FORBIDDEN", "直耗验收不得进入普通库存仓库");
            }
        } else {
            MatWarehouse warehouse = warehouseMapper.selectById(receipt.getWarehouseId());
            if (warehouse == null || !Objects.equals(receipt.getTenantId(), warehouse.getTenantId())
                    || !Objects.equals(receipt.getProjectId(), warehouse.getProjectId())
                    || !"ENABLE".equals(warehouse.getStatus())) {
                throw new BusinessException("WAREHOUSE_INVALID", "入库仓库不存在、已停用或不属于验收项目");
            }
        }
        return order;
    }

    private MatPurchaseOrderItem validateOrderItem(MatReceipt receipt, MatReceiptItem item) {
        if (item.getOrderItemId() == null || item.getMaterialId() == null) {
            throw new BusinessException("RECEIPT_ITEM_INCOMPLETE", "验收明细缺少订单明细或物料");
        }
        MatPurchaseOrderItem orderItem = purchaseOrderItemMapper.selectById(item.getOrderItemId());
        if (orderItem == null || !Objects.equals(receipt.getTenantId(), orderItem.getTenantId())
                || !Objects.equals(receipt.getOrderId(), orderItem.getOrderId())
                || !Objects.equals(item.getMaterialId(), orderItem.getMaterialId())) {
            throw new BusinessException("ORDER_ITEM_MISMATCH", "验收明细与采购订单明细不一致");
        }
        BigDecimal actual = item.getActualQuantity();
        BigDecimal qualified = item.getQualifiedQuantity();
        if (actual == null || actual.signum() <= 0 || qualified == null || qualified.signum() < 0
                || qualified.compareTo(actual) > 0) {
            throw new BusinessException("RECEIPT_QUANTITY_INVALID", "验收明细数量非法");
        }
        if (isDirectConsumption(receipt)
                && (item.getUseLocation() == null || item.getUseLocation().isBlank())) {
            throw new BusinessException("DIRECT_RECEIPT_USE_LOCATION_REQUIRED", "直耗验收明细必须填写使用部位");
        }
        return orderItem;
    }

    private void confirmReceivedQuantity(Long orderItemId, BigDecimal quantity, Long tenantId) {
        for (int retry = 0; retry < 3; retry++) {
            MatPurchaseOrderItem current = purchaseOrderItemMapper.selectById(orderItemId);
            if (current == null || !Objects.equals(tenantId, current.getTenantId())) {
                throw new BusinessException("ORDER_ITEM_MISMATCH", "采购订单明细不存在");
            }
            BigDecimal received = nvl(current.getReceivedQuantity());
            BigDecimal next = received.add(quantity);
            if (next.compareTo(nvl(current.getQuantity())) > 0) {
                throw new BusinessException("RECEIPT_EXCEEDS_ORDER", "累计合格验收数量超过采购订单数量");
            }
            current.setReceivedQuantity(next);
            if (purchaseOrderItemMapper.updateById(current) == 1) {
                return;
            }
        }
        throw new BusinessException("ORDER_ITEM_CONCURRENT_CONFLICT", "采购订单明细并发更新冲突，请稍后重试");
    }

    private void updateOrderCompletion(MatPurchaseOrder order) {
        List<MatPurchaseOrderItem> orderItems = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getOrderId, order.getId())
                        .eq(MatPurchaseOrderItem::getTenantId, order.getTenantId()));
        boolean completed = !orderItems.isEmpty() && orderItems.stream().allMatch(item ->
                nvl(item.getReceivedQuantity()).compareTo(nvl(item.getQuantity())) >= 0);
        if (completed) {
            purchaseOrderMapper.update(null, new LambdaUpdateWrapper<MatPurchaseOrder>()
                    .eq(MatPurchaseOrder::getId, order.getId())
                    .eq(MatPurchaseOrder::getOrderStatus, "APPROVED")
                    .set(MatPurchaseOrder::getOrderStatus, "COMPLETED"));
        }
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private boolean isDirectConsumption(MatReceipt receipt) {
        return "DIRECT_CONSUMPTION".equals(receipt.getReceiptMode());
    }
}
