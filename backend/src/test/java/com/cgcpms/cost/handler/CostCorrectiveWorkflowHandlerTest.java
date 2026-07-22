package com.cgcpms.cost.handler;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.service.CostControlService;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostCorrectiveWorkflowHandlerTest {
    @Mock JdbcTemplate jdbc;
    @Mock ObjectProvider<CostControlService> serviceProvider;

    @Test
    void beforeSubmitFailsClosedWhenResponsibleMemberIsInvalid() {
        CostCorrectiveWorkflowHandler handler = new CostCorrectiveWorkflowHandler(jdbc, serviceProvider);
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq(99L), eq(0L))).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> handler.beforeSubmit(context(99L, 0L)));

        assertEquals("COST_CORRECTIVE_INCOMPLETE", error.getCode());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Integer.class), eq(99L), eq(0L));
        assertTrue(sql.getValue().contains("pm_project_member"));
        assertTrue(sql.getValue().contains("u.status='ENABLE'"));
    }

    private WorkflowContext context(long businessId, long tenantId) {
        WfInstance instance = new WfInstance();
        instance.setBusinessId(businessId);
        instance.setTenantId(tenantId);
        WorkflowContext context = new WorkflowContext();
        context.setInstance(instance);
        return context;
    }
}
