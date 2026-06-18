package com.cgcpms.receipt.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business handler for material receipt approval workflows.
 * On approval, auto-generates material cost records via CostGenerationService.
 * Critical handler: cost generation failure rolls back the entire approval transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialReceiptWorkflowHandler implements WorkflowBusinessHandler {

    private final MatReceiptMapper receiptMapper;
    private final CostGenerationService costGenerationService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.MATERIAL_RECEIPT;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    @Transactional
    public void onApproved(WorkflowContext context) {
        Long receiptId = resolveReceiptId(context.getInstance());
        log.info("材料验收审批通过，更新状态并生成成本 receiptId={}", receiptId);

        receiptMapper.update(null, new LambdaUpdateWrapper<MatReceipt>()
                .eq(MatReceipt::getId, receiptId)
                .set(MatReceipt::getApprovalStatus, "APPROVED"));

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
