package com.cgcpms.purchase.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.purchase.service.PurchaseRequestConversionService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business handler for purchase request approval workflows.
 * On approval: updates status to APPROVED, then converts to purchase order.
 * Critical handler: callback failures roll back the entire approval transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseRequestWorkflowHandler implements WorkflowBusinessHandler {

    private final MatPurchaseRequestMapper requestMapper;
    private final PurchaseRequestConversionService conversionService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.PURCHASE_REQUEST;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(WorkflowContext context) {
        Long requestId = resolveRequestId(context.getInstance());
        log.info("采购申请审批通过，开始处理 requestId={}", requestId);

        MatPurchaseRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException("REQUEST_NOT_FOUND", "采购申请不存在");
        }

        // Update approval status to APPROVED and status to APPROVED
        requestMapper.update(null, new LambdaUpdateWrapper<MatPurchaseRequest>()
                .eq(MatPurchaseRequest::getId, requestId)
                .set(MatPurchaseRequest::getApprovalStatus, "APPROVED")
                .set(MatPurchaseRequest::getStatus, "APPROVED"));

        request.setStatus("APPROVED");
        conversionService.convertApprovedRequest(request);
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long requestId = resolveRequestId(context.getInstance());
        log.info("采购申请审批驳回 requestId={}", requestId);

        requestMapper.update(null, new LambdaUpdateWrapper<MatPurchaseRequest>()
                .eq(MatPurchaseRequest::getId, requestId)
                .set(MatPurchaseRequest::getApprovalStatus, "REJECTED"));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long requestId = resolveRequestId(context.getInstance());
        log.info("采购申请审批撤回，恢复为草稿 requestId={}", requestId);

        requestMapper.update(null, new LambdaUpdateWrapper<MatPurchaseRequest>()
                .eq(MatPurchaseRequest::getId, requestId)
                .set(MatPurchaseRequest::getApprovalStatus, "DRAFT"));
    }

    private Long resolveRequestId(WfInstance instance) {
        Long requestId = instance.getBusinessId();
        if (requestId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（采购申请ID），instanceId=" + instance.getId());
        }
        return requestId;
    }

}
