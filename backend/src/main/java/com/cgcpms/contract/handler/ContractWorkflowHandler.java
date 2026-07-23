package com.cgcpms.contract.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Business handler for contract approval workflows.
 * Critical handler: callback failures roll back the entire approval transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractWorkflowHandler implements WorkflowBusinessHandler {

    private final CtContractMapper contractMapper;
    private final CostGenerationService costGenerationService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.CONTRACT_APPROVAL;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        WfInstance instance = context.getInstance();
        Long contractId = resolveContractId(instance);
        log.info("合同提交/重提审批，进入审批中 contractId={}", contractId);

        int updated = contractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                .eq(CtContract::getId, contractId)
                .eq(CtContract::getTenantId, instance.getTenantId())
                .in(CtContract::getApprovalStatus,
                        ContractStatusConstants.APPROVAL_DRAFT,
                        ContractStatusConstants.APPROVAL_REJECTED,
                        ContractStatusConstants.APPROVAL_WITHDRAWN,
                        ContractStatusConstants.APPROVAL_APPROVING)
                .set(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVING));
        requireSingleStatusTransition(updated, contractId, "提交/重提");
    }

    @Override
    public void onApproved(WorkflowContext context) {
        WfInstance instance = context.getInstance();
        Long contractId = resolveContractId(instance);
        log.info("合同审批通过，更新状态并生成锁定成本 contractId={}", contractId);

        int updated = contractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                .eq(CtContract::getId, contractId)
                .eq(CtContract::getTenantId, instance.getTenantId())
                .eq(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVING)
                .set(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVED)
                .set(CtContract::getContractStatus, ContractStatusConstants.STATUS_PERFORMING));
        requireSingleStatusTransition(updated, contractId, "审批通过");

        costGenerationService.generateLockedCost(contractId);
    }

    @Override
    public void onRejected(WorkflowContext context) {
        WfInstance instance = context.getInstance();
        Long contractId = resolveContractId(instance);
        log.info("合同审批驳回 contractId={}", contractId);

        int updated = contractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                .eq(CtContract::getId, contractId)
                .eq(CtContract::getTenantId, instance.getTenantId())
                .eq(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVING)
                .set(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_REJECTED));
        requireSingleStatusTransition(updated, contractId, "审批驳回");
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        WfInstance instance = context.getInstance();
        Long contractId = resolveContractId(instance);
        log.info("合同审批撤回，恢复为草稿 contractId={}", contractId);

        int updated = contractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                .eq(CtContract::getId, contractId)
                .eq(CtContract::getTenantId, instance.getTenantId())
                .eq(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVING)
                .set(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_DRAFT));
        requireSingleStatusTransition(updated, contractId, "审批撤回");
    }

    private Long resolveContractId(WfInstance instance) {
        Long contractId = instance.getBusinessId();
        if (contractId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（合同ID），instanceId=" + instance.getId());
        }
        return contractId;
    }

    private void requireSingleStatusTransition(int updated, Long contractId, String action) {
        if (updated != 1) {
            throw new IllegalStateException("合同" + action + "状态同步失败 contractId=" + contractId);
        }
    }
}
