package com.cgcpms.receipt.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.inventory.service.MatStockService;
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

        // 1. Update receipt approval status
        receiptMapper.update(null, new LambdaUpdateWrapper<MatReceipt>()
                .eq(MatReceipt::getId, receiptId)
                .set(MatReceipt::getApprovalStatus, "APPROVED"));

        // 2. Stock-in: transfer qualified quantity to inventory
        MatReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt != null && receipt.getWarehouseId() != null) {
            List<MatReceiptItem> items = receiptItemMapper.selectList(
                    new LambdaQueryWrapper<MatReceiptItem>()
                            .eq(MatReceiptItem::getReceiptId, receiptId));
            for (MatReceiptItem item : items) {
                if (item.getMaterialId() == null || item.getQualifiedQuantity() == null) {
                    continue;
                }
                BigDecimal qty = item.getQualifiedQuantity();
                if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                log.info("验收自动入库 receiptId={} warehouseId={} materialId={} qty={}",
                        receiptId, receipt.getWarehouseId(), item.getMaterialId(), qty);
                matStockService.stockIn(receipt.getWarehouseId(), item.getMaterialId(),
                        qty, "MAT_RECEIPT", receiptId);
            }
        } else {
            log.warn("验收单未指定仓库，跳过自动入库 receiptId={}", receiptId);
        }

        // 3. Generate cost records
        costGenerationService.generateCost("MAT_RECEIPT", receiptId);
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
}
