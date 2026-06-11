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
    public void onApproved(WorkflowContext context) {
        Long contractId = resolveContractId(context.getInstance());
        log.info("合同审批通过，更新状态并生成锁定成本 contractId={}", contractId);

        contractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                .eq(CtContract::getId, contractId)
                .set(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVED)
                .set(CtContract::getContractStatus, ContractStatusConstants.STATUS_PERFORMING));

        costGenerationService.generateLockedCost(contractId);
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long contractId = resolveContractId(context.getInstance());
        log.info("合同审批驳回 contractId={}", contractId);

        contractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                .eq(CtContract::getId, contractId)
                .set(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_REJECTED));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long contractId = resolveContractId(context.getInstance());
        log.info("合同审批撤回，恢复为草稿 contractId={}", contractId);

        contractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                .eq(CtContract::getId, contractId)
                .set(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_DRAFT));
    }

    private Long resolveContractId(WfInstance instance) {
        Long contractId = instance.getBusinessId();
        if (contractId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（合同ID），instanceId=" + instance.getId());
        }
        return contractId;
    }
}
