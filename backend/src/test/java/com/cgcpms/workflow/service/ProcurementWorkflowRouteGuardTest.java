package com.cgcpms.workflow.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfNodeInstance;
import com.cgcpms.workflow.WorkflowConstants;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.*;

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
        com.cgcpms.system.mapper.SysUserMapper users = mock(com.cgcpms.system.mapper.SysUserMapper.class);
        WorkflowNotificationAlertService alerts = mock(WorkflowNotificationAlertService.class);
        WorkflowApprovalService service = new WorkflowApprovalService(core, instances, nodes, tasks, users, alerts);
        WfTask task = new WfTask();
        task.setId(20L);
        task.setTenantId(0L);
        task.setInstanceId(21L);
        task.setNodeInstanceId(22L);
        task.setBusinessType(WorkflowBusinessTypes.PURCHASE_REQUEST);
        task.setTaskStatus(WorkflowConstants.TASK_PENDING);
        task.setApproverId(1L);
        task.setTaskVersion(0);
        WfInstance instance = new WfInstance();
        instance.setId(21L);
        instance.setTenantId(0L);
        instance.setInstanceStatus(WorkflowConstants.INSTANCE_RUNNING);
        WfNodeInstance node = new WfNodeInstance();
        node.setId(22L);
        node.setTenantId(0L);
        node.setNodeStatus(WorkflowConstants.NODE_ACTIVE);
        when(tasks.selectByIdIgnoringTenant(20L)).thenReturn(task);
        when(instances.pingInstanceRunning(21L, WorkflowConstants.INSTANCE_RUNNING)).thenReturn(1);
        when(instances.selectByIdForUpdate(21L, 0L)).thenReturn(instance);
        when(nodes.selectByIdForUpdate(22L, 0L)).thenReturn(node);
        when(tasks.selectByIdForUpdate(20L, 0L)).thenReturn(task);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.approve(20L, 1L, "approver", "同意", "purchase-request-route-20"));

        assertEquals("PURCHASE_REQUEST_DEDICATED_APPROVAL_REQUIRED", error.getCode());
        verify(tasks, never()).updateTaskStatusWithCas(anyLong(), anyString(), anyInt(), anyString(),
                anyString(), nullable(String.class), any(), anyLong());
        verifyNoInteractions(alerts);
    }
}
