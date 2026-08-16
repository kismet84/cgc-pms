package com.cgcpms.quality.handler;

import com.cgcpms.quality.service.QualitySafetyService;
import com.cgcpms.cost.service.CostClassificationGuard;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QualityConsequenceWorkflowHandler implements WorkflowBusinessHandler {
    private final ObjectProvider<QualitySafetyService> serviceProvider;
    private final CostClassificationGuard costClassificationGuard;

    @Override public String supportBusinessType() { return WorkflowBusinessTypes.QS_CONSEQUENCE; }
    @Override public boolean isCritical() { return true; }
    @Override public void beforeSubmit(WorkflowContext context) {
        costClassificationGuard.requireClassified("QUALITY_SAFETY_CONSEQUENCE", businessId(context));
        onRunning(context);
    }
    @Override public void onRunning(WorkflowContext context) { serviceProvider.getObject().onConsequenceRunning(context.getInstance()); }
    @Override public void onApproved(WorkflowContext context) { serviceProvider.getObject().onConsequenceApproved(context.getInstance()); }
    @Override public void onRejected(WorkflowContext context) {
        serviceProvider.getObject().onConsequenceRejected(context.getInstance());
        costClassificationGuard.voidPending("QUALITY_SAFETY_CONSEQUENCE", businessId(context));
    }
    @Override public void onWithdrawn(WorkflowContext context) {
        serviceProvider.getObject().onConsequenceWithdrawn(context.getInstance());
        costClassificationGuard.voidPending("QUALITY_SAFETY_CONSEQUENCE", businessId(context));
    }

    private Long businessId(WorkflowContext context) {
        Long id = context.getInstance().getBusinessId();
        if (id == null) throw new IllegalStateException("质量安全金额后果审批缺少业务ID");
        return id;
    }
}
