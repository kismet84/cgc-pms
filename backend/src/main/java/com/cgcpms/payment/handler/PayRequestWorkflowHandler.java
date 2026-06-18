package com.cgcpms.payment.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.PAY_REQUEST;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void onApproved(WorkflowContext context) {
        Long payAppId = resolveBusinessId(context.getInstance());
        log.info("付款申请审批通过，重新校验并更新状态 payAppId={}", payAppId);

        // Re-validate at approval time (authoritative two-phase validation)
        PayApplication app = payApplicationMapper.selectById(payAppId);
        if (app == null) {
            throw new IllegalStateException("付款申请不存在 payAppId=" + payAppId);
        }
        payApplicationService.validatePaymentAmount(app);

        int rows = payApplicationMapper.update(null, new LambdaUpdateWrapper<PayApplication>()
                .eq(PayApplication::getId, payAppId)
                .set(PayApplication::getApprovalStatus, "APPROVED")
                .set(PayApplication::getPayStatus, "APPROVED"));
        if (rows != 1) {
            throw new BusinessException("PAY_APP_STATUS_CONFLICT",
                    "付款申请记录不存在或已被并发更新，请刷新后重试");
        }
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long payAppId = resolveBusinessId(context.getInstance());
        log.info("付款申请审批驳回 payAppId={}", payAppId);

        payApplicationMapper.update(null, new LambdaUpdateWrapper<PayApplication>()
                .eq(PayApplication::getId, payAppId)
                .set(PayApplication::getApprovalStatus, "REJECTED"));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long payAppId = resolveBusinessId(context.getInstance());
        log.info("付款申请审批撤回，恢复为草稿 payAppId={}", payAppId);

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
}
