package com.cgcpms.revenue.handler;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.revenue.service.RevenueOperationsService;
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
public class OwnerSettlementWorkflowHandler implements WorkflowBusinessHandler {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<RevenueOperationsService> serviceProvider;

    @Override public String supportBusinessType() { return WorkflowBusinessTypes.OWNER_SETTLEMENT; }
    @Override public boolean isCritical() { return true; }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        Long id = id(context.getInstance());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM owner_settlement WHERE id=? AND tenant_id=? AND status IN('DRAFT','REJECTED') AND attachment_count>0", Integer.class, id, context.getInstance().getTenantId());
        if (count == null || count != 1) throw new BusinessException("OWNER_SETTLEMENT_INCOMPLETE", "业主结算不存在、状态不正确或缺少附件");
    }

    @Override public void onApproved(WorkflowContext context) { serviceProvider.getObject().onSettlementApproved(id(context.getInstance())); }
    @Override public void onRejected(WorkflowContext context) { serviceProvider.getObject().onSettlementRejected(id(context.getInstance())); }

    private Long id(WfInstance instance) {
        if (instance.getBusinessId() == null) throw new IllegalStateException("业主结算审批实例缺少业务ID");
        return instance.getBusinessId();
    }
}
