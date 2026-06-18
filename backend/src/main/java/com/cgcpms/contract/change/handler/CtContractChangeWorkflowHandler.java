package com.cgcpms.contract.change.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.util.BigDecimalUtils;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Business handler for contract change (CT_CHANGE) approval workflows.
 * On approval:
 *   - Updates approval status to APPROVED and marks effective
 *   - Updates ct_contract.currentAmount (NOT contractAmount) by adding changeAmount
 *   - Generates cost record via CostGenerationService
 *   - Refreshes cost_summary
 * Critical handler: cost generation failure rolls back the entire approval transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CtContractChangeWorkflowHandler implements WorkflowBusinessHandler {

    private final CtContractChangeMapper changeMapper;
    private final CtContractMapper contractMapper;
    private final CostGenerationService costGenerationService;
    private final CostSummaryService costSummaryService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.CT_CHANGE;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    /**
     * 审批通过回调。事务性操作：update change 状态 → update contract currentAmount → generateCost。
     * 若 generateCost 抛出 RuntimeException（含 BusinessException），整个事务回滚，
     * change 状态和 contract currentAmount 均不会生效。
     */
    @Override
    public void onApproved(WorkflowContext context) {
        Long changeId = resolveChangeId(context.getInstance());
        log.info("合同变更审批通过，更新状态、合同金额并生成成本 changeId={}", changeId);

        CtContractChange change = changeMapper.selectById(changeId);
        if (change == null) {
            throw new IllegalStateException("合同变更不存在，changeId=" + changeId);
        }

        // 1. Update change: approval status + effective flag
        changeMapper.update(null, new LambdaUpdateWrapper<CtContractChange>()
                .eq(CtContractChange::getId, changeId)
                .set(CtContractChange::getApprovalStatus, "APPROVED")
                .set(CtContractChange::getEffectiveFlag, 1));

        // 2. Atomic increment: UPDATE ct_contract SET current_amount = current_amount + ?
        //    WHERE id = ? (read-modify-write eliminated)
        //    (NOT contractAmount — contractAmount is the original signed amount)
        BigDecimal changeAmount = BigDecimalUtils.nvl(change.getChangeAmount());
        if (changeAmount.compareTo(BigDecimal.ZERO) != 0) {
            // Pessimistic lock: ensure contract exists and prevent concurrent modifications
            CtContract contract = contractMapper.selectByIdForUpdate(change.getContractId());
            if (contract == null) {
                throw new IllegalStateException("合同不存在，contractId=" + change.getContractId());
            }

            contractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                    .eq(CtContract::getId, change.getContractId())
                    .setIncrBy(CtContract::getCurrentAmount, changeAmount));

            log.info("合同 currentAmount 原子递增: contractId={}, changeAmount={}, oldCurrentAmount={}",
                    change.getContractId(), changeAmount, contract.getCurrentAmount());
        }

        // 3. Generate cost record
        costGenerationService.generateCost("CT_CHANGE", changeId);

        // 4. Refresh cost_summary for the project
        if (change.getProjectId() != null) {
            costSummaryService.refreshSummary(change.getTenantId(), change.getProjectId());
        }
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long changeId = resolveChangeId(context.getInstance());
        log.info("合同变更审批驳回 changeId={}", changeId);

        changeMapper.update(null, new LambdaUpdateWrapper<CtContractChange>()
                .eq(CtContractChange::getId, changeId)
                .set(CtContractChange::getApprovalStatus, "REJECTED"));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long changeId = resolveChangeId(context.getInstance());
        log.info("合同变更审批撤回，恢复为草稿 changeId={}", changeId);

        changeMapper.update(null, new LambdaUpdateWrapper<CtContractChange>()
                .eq(CtContractChange::getId, changeId)
                .set(CtContractChange::getApprovalStatus, "DRAFT"));
    }

    private Long resolveChangeId(WfInstance instance) {
        Long changeId = instance.getBusinessId();
        if (changeId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（合同变更ID），instanceId=" + instance.getId());
        }
        return changeId;
    }
}
