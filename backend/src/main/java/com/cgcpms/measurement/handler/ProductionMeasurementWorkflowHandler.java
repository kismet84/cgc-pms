package com.cgcpms.measurement.handler;

import com.cgcpms.measurement.service.ProductionMeasurementService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductionMeasurementWorkflowHandler implements WorkflowBusinessHandler {
    private final ObjectProvider<ProductionMeasurementService> serviceProvider;

    @Override public String supportBusinessType() { return WorkflowBusinessTypes.PRODUCTION_MEASUREMENT; }
    @Override public boolean isCritical() { return true; }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        serviceProvider.getObject().validateAndSyncEvidence(id(context.getInstance()));
    }

    @Override public void onApproved(WorkflowContext context) { serviceProvider.getObject().onApproved(id(context.getInstance())); }
    @Override public void onRejected(WorkflowContext context) { serviceProvider.getObject().onRejected(id(context.getInstance())); }

    private Long id(WfInstance instance) {
        if (instance.getBusinessId() == null) throw new IllegalStateException("产值计量审批实例缺少业务ID");
        return instance.getBusinessId();
    }
}
