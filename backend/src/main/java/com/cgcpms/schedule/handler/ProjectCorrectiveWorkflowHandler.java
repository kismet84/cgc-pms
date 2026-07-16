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
public class ProjectCorrectiveWorkflowHandler implements WorkflowBusinessHandler {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<ProjectScheduleService> serviceProvider;

    @Override public String supportBusinessType() { return WorkflowBusinessTypes.PROJECT_CORRECTIVE_ACTION; }
    @Override public boolean isCritical() { return true; }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        Long id = businessId(context.getInstance());
        Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM project_corrective_action c JOIN project_progress_snapshot s ON s.id=c.snapshot_id WHERE c.id=? AND c.tenant_id=? AND c.status IN('DRAFT','REJECTED') AND c.deleted_flag=0 AND s.status IN('LAGGING','OVERDUE')", Integer.class, id, context.getInstance().getTenantId());
        if (valid == null || valid != 1) throw new BusinessException("PROJECT_CORRECTIVE_INCOMPLETE", "纠偏单不存在、状态不正确或偏差快照不需要纠偏");
    }

    @Override public void onApproved(WorkflowContext context) { serviceProvider.getObject().onCorrectiveApproved(businessId(context.getInstance())); }
    @Override public void onRejected(WorkflowContext context) { serviceProvider.getObject().onCorrectiveRejected(businessId(context.getInstance())); }
    @Override public void onWithdrawn(WorkflowContext context) { serviceProvider.getObject().onCorrectiveRejected(businessId(context.getInstance())); }

    private Long businessId(WfInstance instance) {
        if (instance.getBusinessId() == null) throw new IllegalStateException("项目纠偏审批实例缺少业务ID");
        return instance.getBusinessId();
    }
}
