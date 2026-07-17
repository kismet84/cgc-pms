package com.cgcpms.closeout.handler;

import com.cgcpms.closeout.service.ProjectCloseoutService;
import com.cgcpms.common.exception.BusinessException;
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
public class ProjectFinalAcceptanceWorkflowHandler implements WorkflowBusinessHandler {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<ProjectCloseoutService> serviceProvider;

    @Override
    public String supportBusinessType() { return WorkflowBusinessTypes.PROJECT_FINAL_ACCEPTANCE; }

    @Override
    public boolean isCritical() { return true; }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        Long id = businessId(context.getInstance());
        Integer valid = jdbc.queryForObject("""
                SELECT COUNT(*) FROM closeout_final_acceptance a
                WHERE a.id=? AND a.tenant_id=? AND a.status IN('DRAFT','REJECTED') AND a.conclusion='PASS' AND a.deleted_flag=0
                 AND EXISTS(SELECT 1 FROM sys_file f WHERE f.tenant_id=a.tenant_id
                   AND f.business_type='CLOSEOUT_FINAL_ACCEPTANCE' AND f.business_id=a.id
                   AND f.document_type='FINAL_ACCEPTANCE_CERTIFICATE' AND f.virus_scan_status='CLEAN' AND f.deleted_flag=0)
                """, Integer.class, id, context.getInstance().getTenantId());
        if (valid == null || valid != 1)
            throw new BusinessException("CLOSEOUT_FINAL_INCOMPLETE", "竣工验收不存在、状态不正确、结论未通过或缺少验收证明");
    }

    @Override
    public void onApproved(WorkflowContext context) { serviceProvider.getObject().onFinalAcceptanceApproved(businessId(context.getInstance())); }

    @Override
    public void onRejected(WorkflowContext context) { serviceProvider.getObject().onFinalAcceptanceRejected(businessId(context.getInstance())); }

    @Override
    public void onWithdrawn(WorkflowContext context) { serviceProvider.getObject().onFinalAcceptanceRejected(businessId(context.getInstance())); }

    private Long businessId(WfInstance instance) {
        if (instance.getBusinessId() == null) throw new IllegalStateException("竣工验收审批实例缺少业务ID");
        return instance.getBusinessId();
    }
}
