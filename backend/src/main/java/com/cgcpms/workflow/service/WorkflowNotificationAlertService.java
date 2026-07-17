package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.service.AlertLifecycleService;
import com.cgcpms.notification.entity.SysNotification;
import com.cgcpms.notification.service.NotificationService;
import com.cgcpms.workflow.entity.WfInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowNotificationAlertService {

    private static final String ALERT_RULE_TYPE = "WORKFLOW_NOTIFICATION_FAILED";

    private final NotificationService notificationService;
    private final AlertLogMapper alertLogMapper;
    private final AlertLifecycleService lifecycleService;

    public SysNotification createWorkflowNotification(WfInstance instance, Long recipientUserId,
                                                      String title, String content, String eventType) {
        try {
            return notificationService.create(instance.getTenantId(), recipientUserId, title, content,
                    "WORKFLOW", instance.getId());
        } catch (Exception e) {
            recordNotificationFailure(instance, recipientUserId, title, content, eventType, e);
            return null;
        }
    }

    private void recordNotificationFailure(WfInstance instance, Long recipientUserId,
                                           String title, String content, String eventType, Exception cause) {
        String normalizedEventType = normalizeEventType(eventType);
        String dedupKey = "WF:" + instance.getId() + ":" + normalizedEventType + ":" + recipientUserId;
        try {
            Long existing = alertLogMapper.selectCount(new LambdaQueryWrapper<AlertLog>()
                    .eq(AlertLog::getTenantId, instance.getTenantId())
                    .eq(AlertLog::getDedupKey, dedupKey)
                    .eq(AlertLog::getProcessStatus, "OPEN")
                    .eq(AlertLog::getDeletedFlag, 0));
            if (existing != null && existing > 0) {
                log.warn("Workflow notification failure already alerted: dedupKey={}", dedupKey);
                return;
            }

            AlertLog alert = new AlertLog();
            alert.setTenantId(instance.getTenantId());
            alert.setProjectId(instance.getProjectId());
            alert.setContractId(instance.getContractId());
            alert.setAlertDomain("WORKFLOW");
            alert.setAlertCategory("WORKFLOW_NOTIFICATION");
            alert.setSourceType("WORKFLOW");
            alert.setSourceId(instance.getId());
            alert.setDedupKey(dedupKey);
            alert.setRuleType(ALERT_RULE_TYPE);
            alert.setSeverity("HIGH");
            alert.setMessage(buildMessage(instance, recipientUserId, title, content, normalizedEventType, cause));
            alert.setTriggeredAt(LocalDateTime.now());
            alert.setIsRead(0);
            alert.setProcessStatus("OPEN");
            alert.setDeletedFlag(0);
            lifecycleService.initialize(alert);
            alertLogMapper.insert(alert);
            lifecycleService.recordCreated(alert);
        } catch (Exception alertError) {
            log.warn("Failed to record workflow notification alert: instanceId={}, recipientUserId={}, eventType={}",
                    instance.getId(), recipientUserId, normalizedEventType, alertError);
        }
    }

    private String buildMessage(WfInstance instance, Long recipientUserId, String title, String content,
                                String eventType, Exception cause) {
        return "审批通知发送失败，事件=" + eventType
                + "，实例=" + instance.getId()
                + "，接收人=" + recipientUserId
                + "，标题=" + sanitize(title)
                + "，内容=" + sanitize(content)
                + "，原因=" + sanitize(cause.getMessage());
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "UNKNOWN";
        }
        return eventType.trim().toUpperCase(Locale.ROOT);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value;
        sanitized = sanitized.replaceAll("(?i)authorization\\s*[:=]\\s*bearer\\s+[^\\s,;]+",
                "Authorization: Bearer <redacted>");
        sanitized = sanitized.replaceAll("(?i)cookie\\s*[:=]\\s*[^\\s,;]+",
                "Cookie=<redacted>");
        sanitized = sanitized.replaceAll("(?i)password\\s*[:=]\\s*[^\\s,;]+",
                "password=<redacted>");
        sanitized = sanitized.replaceAll("(?i)token\\s*[:=]\\s*[^\\s,;]+",
                "token=<redacted>");
        return sanitized;
    }
}
