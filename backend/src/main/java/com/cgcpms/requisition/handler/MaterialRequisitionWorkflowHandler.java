package com.cgcpms.requisition.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.mapper.MatRequisitionItemMapper;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
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
 * Business handler for material requisition approval workflows.
 * On approval, auto-executes stock-out for each line item.
 * Critical handler: stock-out failure rolls back the entire approval transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialRequisitionWorkflowHandler implements WorkflowBusinessHandler {

    private final MatRequisitionMapper requisitionMapper;
    private final MatRequisitionItemMapper requisitionItemMapper;
    private final MatStockService matStockService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.MATERIAL_REQUISITION;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(WorkflowContext context) {
        Long requisitionId = resolveRequisitionId(context.getInstance());
        log.info("领料申请审批通过，开始出库 requisitionId={}", requisitionId);

        MatRequisition requisition = requisitionMapper.selectById(requisitionId);
        if (requisition == null) {
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");
        }

        if (requisition.getWarehouseId() == null) {
            throw new BusinessException("REQUISITION_NO_WAREHOUSE", "领料申请未指定仓库，无法出库");
        }

        // Load items
        List<MatRequisitionItem> items = requisitionItemMapper.selectList(
                new LambdaQueryWrapper<MatRequisitionItem>()
                        .eq(MatRequisitionItem::getRequisitionId, requisitionId));

        // Stock-out for each item
        for (MatRequisitionItem item : items) {
            if (item.getMaterialId() == null || item.getQuantity() == null) {
                continue;
            }
            BigDecimal qty = item.getQuantity();
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            try {
                log.info("领料自动出库 requisitionId={} warehouseId={} materialId={} qty={}",
                        requisitionId, requisition.getWarehouseId(), item.getMaterialId(), qty);
                matStockService.stockOut(requisition.getWarehouseId(), item.getMaterialId(),
                        qty, "MAT_REQUISITION", requisitionId);
            } catch (BusinessException e) {
                log.error("领料出库失败，驳回审批 requisitionId={} materialId={} error={}",
                        requisitionId, item.getMaterialId(), e.getMessage());
                // Set approval status to REJECTED
                requisitionMapper.update(null, new LambdaUpdateWrapper<MatRequisition>()
                        .eq(MatRequisition::getId, requisitionId)
                        .set(MatRequisition::getApprovalStatus, "REJECTED"));
                throw e;
            }
        }

        // Set stockOutFlag and approval status
        requisitionMapper.update(null, new LambdaUpdateWrapper<MatRequisition>()
                .eq(MatRequisition::getId, requisitionId)
                .set(MatRequisition::getStockOutFlag, 1)
                .set(MatRequisition::getApprovalStatus, "APPROVED"));

        log.info("领料申请审批通过，出库完成 requisitionId={}", requisitionId);
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long requisitionId = resolveRequisitionId(context.getInstance());
        log.info("领料申请审批驳回 requisitionId={}", requisitionId);

        requisitionMapper.update(null, new LambdaUpdateWrapper<MatRequisition>()
                .eq(MatRequisition::getId, requisitionId)
                .set(MatRequisition::getApprovalStatus, "REJECTED"));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long requisitionId = resolveRequisitionId(context.getInstance());
        log.info("领料申请审批撤回，恢复为草稿 requisitionId={}", requisitionId);

        requisitionMapper.update(null, new LambdaUpdateWrapper<MatRequisition>()
                .eq(MatRequisition::getId, requisitionId)
                .set(MatRequisition::getApprovalStatus, "DRAFT"));
    }

    private Long resolveRequisitionId(WfInstance instance) {
        Long requisitionId = instance.getBusinessId();
        if (requisitionId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（领料申请ID），instanceId=" + instance.getId());
        }
        return requisitionId;
    }
}
