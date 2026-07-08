package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.notification.entity.SysNotification;
import com.cgcpms.notification.service.NotificationService;
import com.cgcpms.workflow.entity.WfInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowNotificationAlertService 通知与预警联动")
class WorkflowNotificationAlertServiceTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private AlertLogMapper alertLogMapper;

    @Test
    @DisplayName("审批提交通知成功时只创建站内通知，不生成预警")
    void createsWorkflowNotificationWithoutAlertOnSuccess() {
        WorkflowNotificationAlertService service =
                new WorkflowNotificationAlertService(notificationService, alertLogMapper);
        WfInstance instance = workflowInstance();
        SysNotification notification = new SysNotification();
        notification.setId(7001L);
        when(notificationService.create(10L, 21L, "提交了审批", "提交了审批：合同A", "WORKFLOW", 9001L))
                .thenReturn(notification);

        SysNotification result = service.createWorkflowNotification(instance, 21L,
                "提交了审批", "提交了审批：合同A", "SUBMIT_PENDING");

        assertSame(notification, result);
        verify(notificationService).create(10L, 21L, "提交了审批", "提交了审批：合同A", "WORKFLOW", 9001L);
        verify(alertLogMapper, never()).insert(any(AlertLog.class));
    }

    @Test
    @DisplayName("审批完成通知失败时生成一条可追踪预警且不泄露敏感内容")
    void recordsAlertWhenCompletionNotificationFails() {
        WorkflowNotificationAlertService service =
                new WorkflowNotificationAlertService(notificationService, alertLogMapper);
        WfInstance instance = workflowInstance();
        when(notificationService.create(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new IllegalStateException(
                        "Authorization: Bearer abc Cookie: sid=1 password=secret token=raw"));
        when(alertLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        SysNotification result = service.createWorkflowNotification(instance, 22L,
                "审批已通过 token=raw", "审批已通过：合同A password=secret", "APPROVAL_COMPLETED");

        assertNull(result, "通知失败时不应伪造通知成功结果");
        ArgumentCaptor<AlertLog> alertCaptor = ArgumentCaptor.forClass(AlertLog.class);
        verify(alertLogMapper).insert(alertCaptor.capture());
        AlertLog alert = alertCaptor.getValue();
        assertEquals(10L, alert.getTenantId());
        assertEquals(100L, alert.getProjectId());
        assertEquals(200L, alert.getContractId());
        assertEquals("WORKFLOW", alert.getAlertDomain());
        assertEquals("WORKFLOW_NOTIFICATION_FAILED", alert.getRuleType());
        assertEquals("HIGH", alert.getSeverity());
        assertEquals("WORKFLOW", alert.getSourceType());
        assertEquals(9001L, alert.getSourceId());
        assertEquals("WF:9001:APPROVAL_COMPLETED:22", alert.getDedupKey());
        assertEquals(0, alert.getIsRead());
        assertEquals("OPEN", alert.getProcessStatus());
        assertEquals(0, alert.getDeletedFlag());
        assertNotNull(alert.getTriggeredAt());
        assertFalse(alert.getMessage().contains("Bearer abc"));
        assertFalse(alert.getMessage().contains("sid=1"));
        assertFalse(alert.getMessage().contains("password=secret"));
        assertFalse(alert.getMessage().contains("token=raw"));
    }

    @Test
    @DisplayName("同一审批异常事件已有活跃预警时不重复插入")
    void doesNotDuplicateExistingExceptionAlert() {
        WorkflowNotificationAlertService service =
                new WorkflowNotificationAlertService(notificationService, alertLogMapper);
        WfInstance instance = workflowInstance();
        when(notificationService.create(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new IllegalStateException("notification down"));
        when(alertLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        SysNotification result = service.createWorkflowNotification(instance, 22L,
                "审批通知", "审批通知内容", "SUBMIT_PENDING");

        assertNull(result);
        verify(alertLogMapper, never()).insert(any(AlertLog.class));
    }

    private WfInstance workflowInstance() {
        WfInstance instance = new WfInstance();
        instance.setId(9001L);
        instance.setTenantId(10L);
        instance.setProjectId(100L);
        instance.setContractId(200L);
        instance.setBusinessType("CONTRACT_APPROVAL");
        instance.setBusinessId(300L);
        instance.setTitle("合同A");
        return instance;
    }
}
