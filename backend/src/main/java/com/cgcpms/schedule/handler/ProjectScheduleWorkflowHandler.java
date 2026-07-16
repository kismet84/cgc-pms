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
public class ProjectScheduleWorkflowHandler implements WorkflowBusinessHandler {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<ProjectScheduleService> serviceProvider;

    @Override public String supportBusinessType() { return WorkflowBusinessTypes.PROJECT_SCHEDULE; }
    @Override public boolean isCritical() { return true; }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        Long id = businessId(context.getInstance());
        Integer valid = jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_schedule_plan s
                WHERE s.id=? AND s.tenant_id=? AND s.status IN('DRAFT','REJECTED') AND s.deleted_flag=0
                  AND EXISTS(SELECT 1 FROM project_wbs_task t WHERE t.schedule_plan_id=s.id AND t.tenant_id=s.tenant_id AND t.deleted_flag=0)
                  AND (SELECT COALESCE(SUM(t.weight_percent),0) FROM project_wbs_task t WHERE t.schedule_plan_id=s.id AND t.tenant_id=s.tenant_id AND t.deleted_flag=0)=100
                """, Integer.class, id, context.getInstance().getTenantId());
        if (valid == null || valid != 1) throw new BusinessException("PROJECT_SCHEDULE_INCOMPLETE", "项目计划不存在、状态不正确、缺少WBS或权重合计不等于100%");
    }

    @Override public void onApproved(WorkflowContext context) { serviceProvider.getObject().onScheduleApproved(businessId(context.getInstance())); }
    @Override public void onRejected(WorkflowContext context) { serviceProvider.getObject().onScheduleRejected(businessId(context.getInstance())); }
    @Override public void onWithdrawn(WorkflowContext context) { serviceProvider.getObject().onScheduleRejected(businessId(context.getInstance())); }

    private Long businessId(WfInstance instance) {
        if (instance.getBusinessId() == null) throw new IllegalStateException("项目计划审批实例缺少业务ID");
        return instance.getBusinessId();
    }
}
