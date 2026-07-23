package com.cgcpms.variation.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Business handler for variation order approval workflows.
 * On approval, auto-generates cost records via CostGenerationService (COST direction only).
 * Critical handler: cost generation failure rolls back the entire approval transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VarOrderWorkflowHandler implements WorkflowBusinessHandler {

    private final VarOrderMapper varOrderMapper;
    private final CostGenerationService costGenerationService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.VAR_ORDER;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void onApproved(WorkflowContext context) {
        Long varOrderId = resolveVarOrderId(context.getInstance());
        log.info("签证变更审批通过，先生成本再更新状态 varOrderId={}", varOrderId);

        VarOrder order = varOrderMapper.selectById(varOrderId);
        if (order == null || !java.util.Objects.equals(order.getTenantId(), context.getInstance().getTenantId())) {
            throw new IllegalStateException("签证变更不存在，varOrderId=" + varOrderId);
        }
        if ("APPROVED".equals(order.getApprovalStatus())) return;
        requireMatchingRunningInstance(order, context.getInstance());

        // 内部成本测算大于0即生成成本；兼容历史 COST 方向数据。
        if ((order.getEstimatedCostAmount() != null && order.getEstimatedCostAmount().signum() > 0)
                || "COST".equals(order.getDirection())) {
            costGenerationService.generateCost("VAR_ORDER", varOrderId);
        }

        int updated = varOrderMapper.update(null, new LambdaUpdateWrapper<VarOrder>()
                .eq(VarOrder::getId, varOrderId)
                .eq(VarOrder::getTenantId, context.getInstance().getTenantId())
                .eq(VarOrder::getApprovalStatus, "APPROVING")
                .eq(VarOrder::getInternalApprovalInstanceId, context.getInstance().getId())
                .set(VarOrder::getApprovalStatus, "APPROVED")
                .set(VarOrder::getApprovedAmount, order.getReportedAmount())
                .set(VarOrder::getOwnerStatus, "INTERNAL_APPROVED")
                .setSql("version=version+1"));
        if (updated != 1) throw new IllegalStateException("签证变更审批状态已变化，varOrderId=" + varOrderId);
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long varOrderId = resolveVarOrderId(context.getInstance());
        log.info("签证变更审批驳回 varOrderId={}", varOrderId);

        VarOrder order = varOrderMapper.selectById(varOrderId);
        if (order != null && "REJECTED".equals(order.getApprovalStatus())) return;
        if (order == null) throw new IllegalStateException("签证变更不存在，varOrderId=" + varOrderId);
        requireMatchingRunningInstance(order, context.getInstance());
        int updated = varOrderMapper.update(null, new LambdaUpdateWrapper<VarOrder>()
                .eq(VarOrder::getId, varOrderId)
                .eq(VarOrder::getTenantId, context.getInstance().getTenantId())
                .eq(VarOrder::getApprovalStatus, "APPROVING")
                .eq(VarOrder::getInternalApprovalInstanceId, context.getInstance().getId())
                .set(VarOrder::getApprovalStatus, "REJECTED")
                .set(VarOrder::getOwnerStatus, "NOT_READY")
                .setSql("version=version+1"));
        if (updated != 1) throw new IllegalStateException("签证变更审批状态已变化，varOrderId=" + varOrderId);
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long varOrderId = resolveVarOrderId(context.getInstance());
        log.info("签证变更审批撤回，恢复为草稿 varOrderId={}", varOrderId);

        VarOrder order = varOrderMapper.selectById(varOrderId);
        if (order != null && "DRAFT".equals(order.getApprovalStatus())) return;
        if (order == null) throw new IllegalStateException("签证变更不存在，varOrderId=" + varOrderId);
        requireMatchingRunningInstance(order, context.getInstance());
        int updated = varOrderMapper.update(null, new LambdaUpdateWrapper<VarOrder>()
                .eq(VarOrder::getId, varOrderId)
                .eq(VarOrder::getTenantId, context.getInstance().getTenantId())
                .eq(VarOrder::getApprovalStatus, "APPROVING")
                .eq(VarOrder::getInternalApprovalInstanceId, context.getInstance().getId())
                .set(VarOrder::getApprovalStatus, "DRAFT")
                .set(VarOrder::getOwnerStatus, "NOT_READY")
                .setSql("version=version+1"));
        if (updated != 1) throw new IllegalStateException("签证变更审批状态已变化，varOrderId=" + varOrderId);
    }

    private void requireMatchingRunningInstance(VarOrder order, WfInstance instance) {
        if (!java.util.Objects.equals(order.getTenantId(), instance.getTenantId())
                || !java.util.Objects.equals(order.getInternalApprovalInstanceId(), instance.getId())
                || !"APPROVING".equals(order.getApprovalStatus()))
            throw new IllegalStateException("审批实例与签证变更状态不一致，varOrderId=" + order.getId());
    }

    private Long resolveVarOrderId(WfInstance instance) {
        Long varOrderId = instance.getBusinessId();
        if (varOrderId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（签证变更ID），instanceId=" + instance.getId());
        }
        return varOrderId;
    }
}
