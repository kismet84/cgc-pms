package com.cgcpms.schedule.handler;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.schedule.service.ProjectScheduleService;
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
public class ProjectPeriodPlanWorkflowHandler implements WorkflowBusinessHandler {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<ProjectScheduleService> serviceProvider;

    @Override public String supportBusinessType() { return WorkflowBusinessTypes.PROJECT_PERIOD_PLAN; }
    @Override public boolean isCritical() { return true; }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        Long id = businessId(context.getInstance());
        Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM project_period_plan p WHERE p.id=? AND p.tenant_id=? AND p.status IN('DRAFT','REJECTED') AND p.deleted_flag=0 AND EXISTS(SELECT 1 FROM project_period_plan_item i WHERE i.period_plan_id=p.id AND i.tenant_id=p.tenant_id)", Integer.class, id, context.getInstance().getTenantId());
        if (valid == null || valid != 1) throw new BusinessException("PROJECT_PERIOD_INCOMPLETE", "月周计划不存在、状态不正确或缺少计划任务");
    }

    @Override public void onApproved(WorkflowContext context) { serviceProvider.getObject().onPeriodApproved(businessId(context.getInstance())); }
    @Override public void onRejected(WorkflowContext context) { serviceProvider.getObject().onPeriodRejected(businessId(context.getInstance())); }
    @Override public void onWithdrawn(WorkflowContext context) { serviceProvider.getObject().onPeriodRejected(businessId(context.getInstance())); }

    private Long businessId(WfInstance instance) {
        if (instance.getBusinessId() == null) throw new IllegalStateException("月周计划审批实例缺少业务ID");
        return instance.getBusinessId();
    }
}
