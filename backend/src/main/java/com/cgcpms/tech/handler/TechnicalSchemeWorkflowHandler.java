package com.cgcpms.tech.handler;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.tech.service.TechnicalManagementService;
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
public class TechnicalSchemeWorkflowHandler implements WorkflowBusinessHandler {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<TechnicalManagementService> serviceProvider;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.TECHNICAL_SCHEME;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        Long id = businessId(context.getInstance());
        Integer valid = jdbc.queryForObject("""
                SELECT COUNT(*) FROM technical_scheme s
                WHERE s.id=? AND s.tenant_id=? AND s.status IN('DRAFT','REJECTED') AND s.deleted_flag=0
                  AND EXISTS(SELECT 1 FROM sys_file f WHERE f.tenant_id=s.tenant_id AND f.business_type='TECH_SCHEME'
                    AND f.business_id=s.id AND f.document_type='SCHEME_FILE' AND f.virus_scan_status='CLEAN' AND f.deleted_flag=0)
                """, Integer.class, id, context.getInstance().getTenantId());
        if (valid == null || valid != 1)
            throw new BusinessException("TECH_SCHEME_INCOMPLETE", "技术方案不存在、状态不正确或缺少已通过扫描的方案正文");
    }

    @Override
    public void onApproved(WorkflowContext context) {
        serviceProvider.getObject().onSchemeApproved(businessId(context.getInstance()));
    }

    @Override
    public void onRejected(WorkflowContext context) {
        serviceProvider.getObject().onSchemeRejected(businessId(context.getInstance()));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        serviceProvider.getObject().onSchemeRejected(businessId(context.getInstance()));
    }

    private Long businessId(WfInstance instance) {
        if (instance.getBusinessId() == null) throw new IllegalStateException("技术方案审批实例缺少业务ID");
        return instance.getBusinessId();
    }
}
