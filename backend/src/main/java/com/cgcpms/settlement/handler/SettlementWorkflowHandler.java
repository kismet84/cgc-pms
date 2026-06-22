package com.cgcpms.settlement.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.settlement.constant.SettlementStatusConstants;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.cgcpms.settlement.constant.SettlementStatusConstants.APPROVAL_APPROVED;
import static com.cgcpms.settlement.constant.SettlementStatusConstants.APPROVAL_DRAFT;
import static com.cgcpms.settlement.constant.SettlementStatusConstants.APPROVAL_REJECTED;
import static com.cgcpms.settlement.constant.SettlementStatusConstants.SETTLEMENT_DRAFT;
import static com.cgcpms.settlement.constant.SettlementStatusConstants.SETTLEMENT_FINALIZED;

/**
 * Business handler for settlement approval workflows.
 * <p>
 * Settlement is READ-ONLY aggregation: it NEVER calls CostGenerationService.
 * On approval, the settlement is locked (FINALIZED) and the final amount
 * is written back to the contract's settlementAmount field.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementWorkflowHandler implements WorkflowBusinessHandler {

    private final StlSettlementMapper stlSettlementMapper;
    private final CtContractMapper ctContractMapper;
    private final VarOrderMapper varOrderMapper;
    private final SubMeasureMapper subMeasureMapper;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.SETTLEMENT;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    // ================================================================
    // Pre-submit validation
    // ================================================================

    @Override
    public void beforeSubmit(WorkflowContext context) {
        Long settlementId = resolveBusinessId(context.getInstance());
        StlSettlement settlement = stlSettlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }

        Long contractId = settlement.getContractId();
        Long tenantId = settlement.getTenantId();

        // 1. Check no unapproved variation orders (COST direction)
        long pendingVarCount = varOrderMapper.selectCount(new LambdaQueryWrapper<VarOrder>()
                .eq(VarOrder::getTenantId, tenantId)
                .eq(VarOrder::getContractId, contractId)
                .eq(VarOrder::getDirection, "COST")
                .notIn(VarOrder::getApprovalStatus, "APPROVED", "REJECTED"));
        if (pendingVarCount > 0) {
            throw new BusinessException("SETTLEMENT_PENDING_VAR_ORDER",
                    "存在未审批的签证变更，请等待变更审批完成后再提交结算");
        }

        // 2. Check no unapproved sub measures
        long pendingMeasureCount = subMeasureMapper.selectCount(new LambdaQueryWrapper<SubMeasure>()
                .eq(SubMeasure::getTenantId, tenantId)
                .eq(SubMeasure::getContractId, contractId)
                .notIn(SubMeasure::getApprovalStatus, "APPROVED", "REJECTED"));
        if (pendingMeasureCount > 0) {
            throw new BusinessException("SETTLEMENT_PENDING_MEASURE",
                    "存在未审批的分包计量，请等待计量审批完成后再提交结算");
        }

        log.info("结算前置校验通过 settlementId={} contractId={}", settlementId, contractId);
    }

    // ================================================================
    // Approval — lock settlement + write back to contract
    // ================================================================

    @Override
    public void onApproved(WorkflowContext context) {
        Long settlementId = resolveBusinessId(context.getInstance());
        StlSettlement settlement = stlSettlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw new IllegalStateException("结算单不存在 settlementId=" + settlementId);
        }

        log.info("结算审批通过，锁定结算单并回写合同结算金额 settlementId={}", settlementId);

        // Lock settlement: FINALIZED + set finalizedAt
        // Status guard: only finalize if still in DRAFT (prevents double-finalization via concurrent approvals)
        int rows = stlSettlementMapper.update(null, new LambdaUpdateWrapper<StlSettlement>()
                .eq(StlSettlement::getId, settlementId)
                .eq(StlSettlement::getSettlementStatus, SETTLEMENT_DRAFT)
                .set(StlSettlement::getApprovalStatus, APPROVAL_APPROVED)
                .set(StlSettlement::getSettlementStatus, SETTLEMENT_FINALIZED)
                .set(StlSettlement::getFinalizedAt, LocalDateTime.now()));
        if (rows != 1) {
            throw new BusinessException("SETTLEMENT_STATUS_CONFLICT",
                    "结算单状态冲突：已被并发操作或已审批，请刷新后重试");
        }

        // Write back to contract: settlementAmount = finalAmount
        // NEVER calls CostGenerationService — settlement is pure read-only aggregation
        if (settlement.getContractId() != null && settlement.getFinalAmount() != null) {
            ctContractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                    .eq(CtContract::getId, settlement.getContractId())
                    .set(CtContract::getSettlementAmount, settlement.getFinalAmount()));
        }
    }

    // ================================================================
    // Rejection
    // ================================================================

    @Override
    public void onRejected(WorkflowContext context) {
        Long settlementId = resolveBusinessId(context.getInstance());
        log.info("结算审批驳回 settlementId={}", settlementId);

        stlSettlementMapper.update(null, new LambdaUpdateWrapper<StlSettlement>()
                .eq(StlSettlement::getId, settlementId)
                .set(StlSettlement::getApprovalStatus, APPROVAL_REJECTED));
    }

    // ================================================================
    // Withdrawal — revert to DRAFT
    // ================================================================

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long settlementId = resolveBusinessId(context.getInstance());
        log.info("结算审批撤回，恢复为草稿 settlementId={}", settlementId);

        stlSettlementMapper.update(null, new LambdaUpdateWrapper<StlSettlement>()
                .eq(StlSettlement::getId, settlementId)
                .set(StlSettlement::getApprovalStatus, APPROVAL_DRAFT)
                .set(StlSettlement::getSettlementStatus, SETTLEMENT_DRAFT));
    }

    // ================================================================
    // Helpers
    // ================================================================

    private Long resolveBusinessId(WfInstance instance) {
        Long businessId = instance.getBusinessId();
        if (businessId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（结算单ID），instanceId=" + instance.getId());
        }
        return businessId;
    }
}
