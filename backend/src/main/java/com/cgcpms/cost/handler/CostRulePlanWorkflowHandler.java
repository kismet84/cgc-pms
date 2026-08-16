package com.cgcpms.cost.handler;

import com.cgcpms.cost.service.CostSubjectV2Service;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class CostRulePlanWorkflowHandler implements WorkflowBusinessHandler {
    private final ObjectProvider<CostSubjectV2Service> serviceProvider;

    public CostRulePlanWorkflowHandler(ObjectProvider<CostSubjectV2Service> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.COST_RULE_PLAN;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        service().markRulePlanSubmitted(businessId(context), context.getInstance().getId());
    }

    @Override
    public void onApproved(WorkflowContext context) {
        service().approveRulePlan(businessId(context), context.getInstance().getId());
    }

    @Override
    public void onRejected(WorkflowContext context) {
        service().rejectRulePlan(businessId(context), context.getInstance().getId(), "REJECTED");
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        service().rejectRulePlan(businessId(context), context.getInstance().getId(), "WITHDRAWN");
    }

    private CostSubjectV2Service service() {
        return serviceProvider.getObject();
    }

    private Long businessId(WorkflowContext context) {
        Long id = context.getInstance().getBusinessId();
        if (id == null) {
            throw new IllegalStateException("成本规则方案审批实例缺少业务ID");
        }
        return id;
    }
}
