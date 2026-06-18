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
        if (order == null) {
            throw new IllegalStateException("签证变更不存在，varOrderId=" + varOrderId);
        }

        // 先生成本，再更新状态 — 若 generateCost 失败抛异常，事务回滚，状态不会被错误更新
        if ("COST".equals(order.getDirection())) {
            costGenerationService.generateCost("VAR_ORDER", varOrderId);
        } else {
            log.info("收入向签证暂不处理（第3阶段） varOrderId={}", varOrderId);
        }

        varOrderMapper.update(null, new LambdaUpdateWrapper<VarOrder>()
                .eq(VarOrder::getId, varOrderId)
                .set(VarOrder::getApprovalStatus, "APPROVED"));
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long varOrderId = resolveVarOrderId(context.getInstance());
        log.info("签证变更审批驳回 varOrderId={}", varOrderId);

        varOrderMapper.update(null, new LambdaUpdateWrapper<VarOrder>()
                .eq(VarOrder::getId, varOrderId)
                .set(VarOrder::getApprovalStatus, "REJECTED"));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long varOrderId = resolveVarOrderId(context.getInstance());
        log.info("签证变更审批撤回，恢复为草稿 varOrderId={}", varOrderId);

        varOrderMapper.update(null, new LambdaUpdateWrapper<VarOrder>()
                .eq(VarOrder::getId, varOrderId)
                .set(VarOrder::getApprovalStatus, "DRAFT"));
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
