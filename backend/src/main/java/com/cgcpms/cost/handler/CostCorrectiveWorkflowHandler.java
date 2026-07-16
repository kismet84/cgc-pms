package com.cgcpms.cost.handler;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.service.CostControlService;
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
public class CostCorrectiveWorkflowHandler implements WorkflowBusinessHandler {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<CostControlService> serviceProvider;

    @Override public String supportBusinessType() { return WorkflowBusinessTypes.COST_CORRECTIVE_ACTION; }
    @Override public boolean isCritical() { return true; }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        Long id = businessId(context.getInstance());
        Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM cost_corrective_action c JOIN cost_forecast f ON f.id=c.forecast_id AND f.tenant_id=c.tenant_id WHERE c.id=? AND c.tenant_id=? AND c.status IN('DRAFT','REJECTED') AND c.deleted_flag=0 AND f.status='ACTION_REQUIRED' AND f.cost_variance_amount>0",
                Integer.class, id, context.getInstance().getTenantId());
        if (valid == null || valid != 1) throw new BusinessException("COST_CORRECTIVE_INCOMPLETE", "纠偏措施不存在、状态不正确或预测已不需要纠偏");
    }

    @Override public void onApproved(WorkflowContext context) { serviceProvider.getObject().onCorrectiveApproved(businessId(context.getInstance())); }
    @Override public void onRejected(WorkflowContext context) { serviceProvider.getObject().onCorrectiveRejected(businessId(context.getInstance())); }
    @Override public void onWithdrawn(WorkflowContext context) { serviceProvider.getObject().onCorrectiveRejected(businessId(context.getInstance())); }

    private Long businessId(WfInstance instance) {
        if (instance.getBusinessId() == null) throw new IllegalStateException("成本纠偏审批实例缺少业务ID");
        return instance.getBusinessId();
    }
}
