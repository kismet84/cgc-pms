package com.cgcpms.quality.handler;

import com.cgcpms.quality.service.QualitySafetyService;
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

    @Override public String supportBusinessType() { return WorkflowBusinessTypes.QS_CONSEQUENCE; }
    @Override public boolean isCritical() { return true; }
    @Override public void beforeSubmit(WorkflowContext context) { onRunning(context); }
    @Override public void onRunning(WorkflowContext context) { serviceProvider.getObject().onConsequenceRunning(context.getInstance()); }
    @Override public void onApproved(WorkflowContext context) { serviceProvider.getObject().onConsequenceApproved(context.getInstance()); }
    @Override public void onRejected(WorkflowContext context) { serviceProvider.getObject().onConsequenceRejected(context.getInstance()); }
    @Override public void onWithdrawn(WorkflowContext context) { serviceProvider.getObject().onConsequenceWithdrawn(context.getInstance()); }
}
