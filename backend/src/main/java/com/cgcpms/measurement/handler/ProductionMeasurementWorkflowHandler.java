package com.cgcpms.measurement.handler;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.measurement.service.ProductionMeasurementService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductionMeasurementWorkflowHandler implements WorkflowBusinessHandler {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<ProductionMeasurementService> serviceProvider;

    @Override public String supportBusinessType() { return WorkflowBusinessTypes.PRODUCTION_MEASUREMENT; }
    @Override public boolean isCritical() { return true; }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        Long id = id(context.getInstance());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM production_measurement m WHERE m.id=? AND m.tenant_id=? AND m.status IN('DRAFT','REJECTED') AND m.attachment_count>0 AND EXISTS(SELECT 1 FROM production_measurement_line l WHERE l.measurement_id=m.id AND l.tenant_id=m.tenant_id AND l.evidence_count>0)", Integer.class, id, context.getInstance().getTenantId());
        if (count == null || count != 1) throw new BusinessException("PRODUCTION_MEASUREMENT_INCOMPLETE", "产值计量不存在、状态不正确或缺少计量依据");
    }

    @Override public void onApproved(WorkflowContext context) { serviceProvider.getObject().onApproved(id(context.getInstance())); }
    @Override public void onRejected(WorkflowContext context) { serviceProvider.getObject().onRejected(id(context.getInstance())); }

    private Long id(WfInstance instance) {
        if (instance.getBusinessId() == null) throw new IllegalStateException("产值计量审批实例缺少业务ID");
        return instance.getBusinessId();
    }
}
