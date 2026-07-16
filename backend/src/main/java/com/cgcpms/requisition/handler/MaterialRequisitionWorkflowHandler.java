package com.cgcpms.requisition.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business handler for material requisition approval workflows.
 * Approval grants warehouse issue authorization only. Actual stock-out is a separate warehouse operation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialRequisitionWorkflowHandler implements WorkflowBusinessHandler {

    private final MatRequisitionMapper requisitionMapper;

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
        log.info("领料申请审批通过，形成待出库授权 requisitionId={}", requisitionId);

        MatRequisition requisition = requisitionMapper.selectById(requisitionId);
        if (requisition == null) {
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");
        }

        if ("APPROVED".equals(requisition.getApprovalStatus())) {
            return;
        }
        if (!"APPROVING".equals(requisition.getApprovalStatus())) {
            throw new BusinessException("REQUISITION_STATUS_INVALID", "领料申请当前状态不允许确认审批");
        }

        int updated = requisitionMapper.update(null, new LambdaUpdateWrapper<MatRequisition>()
                .eq(MatRequisition::getId, requisitionId)
                .eq(MatRequisition::getApprovalStatus, "APPROVING")
                .set(MatRequisition::getStockOutFlag, 0)
                .set(MatRequisition::getApprovalStatus, "APPROVED"));
        if (updated != 1) {
            throw new BusinessException("REQUISITION_CONCURRENT_CONFLICT", "领料审批状态已变化，请刷新后重试");
        }

        log.info("领料申请审批通过，等待仓管员实际出库 requisitionId={}", requisitionId);
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
