package com.cgcpms.revenue.handler;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.revenue.entity.ContractRevenue;
import com.cgcpms.revenue.mapper.ContractRevenueMapper;
import com.cgcpms.revenue.service.ContractRevenueService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 收入确认审批工作流处理器。
 * <p>
 * 审批通过 → 生成 cost_item（REVENUE_CONFIRMED） → 刷新成本汇总。
 * 审批驳回 → 恢复为可编辑状态。
 * <p>
 * 使用 ObjectProvider<ContractRevenueService> 打破循环依赖：
 * ContractRevenueService → WorkflowEngine → ... → ContractRevenueWorkflowHandler → ContractRevenueService
 */
@Slf4j
@Component
public class ContractRevenueWorkflowHandler implements WorkflowBusinessHandler {

    private final ContractRevenueMapper mapper;
    private final ObjectProvider<ContractRevenueService> serviceProvider;

    public ContractRevenueWorkflowHandler(ContractRevenueMapper mapper,
                                           ObjectProvider<ContractRevenueService> serviceProvider) {
        this.mapper = mapper;
        this.serviceProvider = serviceProvider;
    }

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.CONTRACT_REVENUE;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        Long revenueId = resolveBusinessId(context.getInstance());
        ContractRevenue revenue = mapper.selectById(revenueId);
        if (revenue == null) {
            throw new BusinessException("REVENUE_NOT_FOUND", "收入确认单不存在");
        }
        if (revenue.getProgressPercent() != null
                && revenue.getProgressPercent().compareTo(new java.math.BigDecimal("100")) > 0) {
            throw new BusinessException("PROGRESS_INVALID", "累计履约进度不能超过 100%");
        }
        log.info("收入确认前置校验通过 revenueId={}", revenueId);
    }

    @Override
    public void onApproved(WorkflowContext context) {
        Long revenueId = resolveBusinessId(context.getInstance());
        log.info("收入确认审批通过 revenueId={}", revenueId);
        serviceProvider.getObject().onApproved(revenueId);
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long revenueId = resolveBusinessId(context.getInstance());
        log.info("收入确认审批驳回 revenueId={}", revenueId);
        serviceProvider.getObject().onRejected(revenueId);
    }

    private Long resolveBusinessId(WfInstance instance) {
        Long businessId = instance.getBusinessId();
        if (businessId == null) {
            throw new IllegalStateException("审批实例缺少业务ID（收入确认单ID），instanceId=" + instance.getId());
        }
        return businessId;
    }
}
