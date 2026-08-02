package com.cgcpms.workflow.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfNodeInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProcurementWorkflowRouteGuardTest {

    @Test
    void genericSubmitRejectsEveryProcurementBusinessBeforeMutation() {
        WorkflowCoreService core = mock(WorkflowCoreService.class);
        WfInstanceMapper instances = mock(WfInstanceMapper.class);
        WfNodeInstanceMapper nodes = mock(WfNodeInstanceMapper.class);
        WfTaskMapper tasks = mock(WfTaskMapper.class);
        WfCcService cc = mock(WfCcService.class);
        WorkflowBusinessAccessValidator access = mock(WorkflowBusinessAccessValidator.class);
        WorkflowNotificationAlertService alerts = mock(WorkflowNotificationAlertService.class);
        WorkflowSubmitService service = new WorkflowSubmitService(core, instances, nodes, tasks, cc, access, alerts);

        for (String businessType : List.of(WorkflowBusinessTypes.PURCHASE_REQUEST,
                WorkflowBusinessTypes.PURCHASE_ORDER, WorkflowBusinessTypes.MATERIAL_RECEIPT)) {
            BusinessException error = assertThrows(BusinessException.class, () -> service.submit(
                    1L, "approver", 0L, businessType, 10L, "采购业务", BigDecimal.ONE,
                    10001L, 30001L, null, null, null));
            assertEquals("PROCUREMENT_DEDICATED_WORKFLOW_REQUIRED", error.getCode());
        }
        verifyNoInteractions(core, instances, nodes, tasks, cc, access, alerts);
    }

    @Test
    void genericApproveRejectsPurchaseRequestBeforeCasMutation() {
        WorkflowCoreService core = mock(WorkflowCoreService.class);
        WfInstanceMapper instances = mock(WfInstanceMapper.class);
        WfNodeInstanceMapper nodes = mock(WfNodeInstanceMapper.class);
        WfTaskMapper tasks = mock(WfTaskMapper.class);
        WorkflowNotificationAlertService alerts = mock(WorkflowNotificationAlertService.class);
        WorkflowApprovalService service = new WorkflowApprovalService(core, instances, nodes, tasks, alerts);
        WfTask task = new WfTask();
        task.setBusinessType(WorkflowBusinessTypes.PURCHASE_REQUEST);
        when(tasks.selectById(20L)).thenReturn(task);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.approve(20L, 1L, "approver", "同意", "purchase-request-route-20"));

        assertEquals("PURCHASE_REQUEST_DEDICATED_APPROVAL_REQUIRED", error.getCode());
        verifyNoInteractions(core, instances, nodes, alerts);
    }
}
