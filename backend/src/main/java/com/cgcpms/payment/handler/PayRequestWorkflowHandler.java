package com.cgcpms.payment.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.accounting.service.EntryGenerator;
import com.cgcpms.accounting.strategy.PayApplicationEntryGenerationStrategy;
import com.cgcpms.audit.service.MandatoryAuditService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.budget.service.ContractBudgetAllocationService;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PaymentApplicationIntegrityService;
import com.cgcpms.payment.service.PaymentApplicationSourceService;
import com.cgcpms.payment.constant.PaymentIntegrityConstants;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/**
 * Business handler for payment request approval workflows.
 * Critical handler: payment approval failure triggers transaction rollback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayRequestWorkflowHandler implements WorkflowBusinessHandler {

    private final PayApplicationMapper payApplicationMapper;
    private final PayApplicationService payApplicationService;
    private final PaymentApplicationIntegrityService integrityService;
    private final PaymentApplicationSourceService sourceService;
    private final ContractBudgetAllocationService contractBudgetAllocationService;
    private final EntryGenerator entryGenerator;
    private final MandatoryAuditService mandatoryAuditService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.PAY_REQUEST;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        WfInstance instance = context.getInstance();
        int currentRound = round(instance);
        if (currentRound <= 1) {
            return;
        }

        Long payAppId = resolveBusinessId(instance);
        PayApplication app = payApplicationService.lockForAmountGate(payAppId);
        if (!"DRAFT".equals(app.getApprovalStatus())) {
            throw new BusinessException("PAY_APP_RESUBMIT_STATUS_INVALID", "只有已恢复为草稿的付款申请可以重新提交");
        }

        payApplicationService.validatePaymentAmount(app);
        if (PaymentIntegrityConstants.CLOSED_LOOP_V1.equals(app.getIntegrityVersion())) {
            integrityService.validateAndAllocateForSubmit(app, currentRound);
        }

        int rows = payApplicationMapper.update(null, new LambdaUpdateWrapper<PayApplication>()
                .eq(PayApplication::getId, payAppId)
                .eq(PayApplication::getTenantId, app.getTenantId())
                .eq(PayApplication::getApprovalStatus, "DRAFT")
                .set(PayApplication::getApprovalStatus, "APPROVING")
                .set(PayApplication::getPayStatus, "PENDING"));
        if (rows != 1) {
            throw new BusinessException("PAY_APP_STATUS_CONFLICT",
                    "付款申请已被并发更新，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(WorkflowContext context) {
        Long payAppId = resolveBusinessId(context.getInstance());
        log.info("付款申请审批通过，重新校验并更新状态 payAppId={}", payAppId);

        // Re-validate at approval time (authoritative two-phase validation)
        PayApplication app = payApplicationService.lockForAmountGate(payAppId);
        if (app == null) {
            throw new IllegalStateException("付款申请不存在 payAppId=" + payAppId);
        }
        payApplicationService.validatePaymentAmount(app);
        if (PaymentIntegrityConstants.CLOSED_LOOP_V1.equals(app.getIntegrityVersion())) {
            integrityService.validateForApproval(app);
        }

        int rows = payApplicationMapper.update(null, new LambdaUpdateWrapper<PayApplication>()
                .eq(PayApplication::getId, payAppId)
                .set(PayApplication::getApprovalStatus, "APPROVED")
                .set(PayApplication::getPayStatus, "APPROVED")
                .set(PayApplication::getApprovedAmount, app.getApplyAmount()));
        if (rows != 1) {
            throw new BusinessException("PAY_APP_STATUS_CONFLICT",
                    "付款申请记录不存在或已被并发更新，请刷新后重试");
        }
        boolean advance = "ADVANCE".equals(app.getPayType());
        if (!advance) {
            entryGenerator.generateEntry(PayApplicationEntryGenerationStrategy.SOURCE_TYPE, payAppId,
                    PayApplicationEntryGenerationStrategy.ENTRY_TYPE);
        }
        mandatoryAuditService.finance("PAY_APPLICATION_CONFIRMED", "PAY_APPLICATION", payAppId,
                app.getProjectId(), "APPROVED", Map.of(
                        "approvedAmount", app.getApplyAmount(),
                        "payType", Objects.toString(app.getPayType(), ""),
                        "confirmsAp", !advance));
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long payAppId = resolveBusinessId(context.getInstance());
        log.info("付款申请审批驳回 payAppId={}", payAppId);

        PayApplication app = requireApplication(payAppId);
        if (PaymentIntegrityConstants.CLOSED_LOOP_V1.equals(app.getIntegrityVersion())) {
            contractBudgetAllocationService.releaseForPayment(app);
            sourceService.releaseAllocations(app, "REJECT", round(context.getInstance()));
        }

        payApplicationMapper.update(null, new LambdaUpdateWrapper<PayApplication>()
                .eq(PayApplication::getId, payAppId)
                .set(PayApplication::getApprovalStatus, "REJECTED"));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long payAppId = resolveBusinessId(context.getInstance());
        log.info("付款申请审批撤回，恢复为草稿 payAppId={}", payAppId);

        PayApplication app = requireApplication(payAppId);
        if (PaymentIntegrityConstants.CLOSED_LOOP_V1.equals(app.getIntegrityVersion())) {
            contractBudgetAllocationService.releaseForPayment(app);
            sourceService.releaseAllocations(app, "WITHDRAW", round(context.getInstance()));
        }

        payApplicationMapper.update(null, new LambdaUpdateWrapper<PayApplication>()
                .eq(PayApplication::getId, payAppId)
                .set(PayApplication::getApprovalStatus, "DRAFT"));
    }

    private Long resolveBusinessId(WfInstance instance) {
        Long businessId = instance.getBusinessId();
        if (businessId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（付款申请ID），instanceId=" + instance.getId());
        }
        return businessId;
    }

    private PayApplication requireApplication(Long id) {
        PayApplication app = payApplicationMapper.selectById(id);
        if (app == null) throw new IllegalStateException("付款申请不存在 payAppId=" + id);
        return app;
    }

    private static int round(WfInstance instance) {
        return instance.getCurrentRound() == null ? 1 : instance.getCurrentRound();
    }
}
