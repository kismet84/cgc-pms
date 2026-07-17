package com.cgcpms.alert.notification;

import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.entity.AlertNotificationSendRecord;
import com.cgcpms.alert.mapper.AlertNotificationSendRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotificationDispatcher {

    private final AlertNotificationSendRecordMapper recordMapper;
    private final List<AlertNotificationSender> senders;
    private final Map<DispatchKey, Object> inAppLocks = new ConcurrentHashMap<>();

    public void dispatchAlertCreated(Long tenantId, Long userId, AlertLog alert, String title) {
        dispatchAlertCreated(tenantId, userId, alert, title, Set.of(AlertNotificationChannel.IN_APP.name()));
    }

    public void dispatchStatusChanged(Long tenantId, Long userId, AlertLog alert, String title, String statusRemark) {
        dispatchStatusChanged(tenantId, userId, alert, title, statusRemark, Set.of(AlertNotificationChannel.IN_APP.name()));
    }

    public void dispatchEscalated(Long tenantId, Long userId, AlertLog alert, int level, String reason) {
        String title = level >= 2 ? "预警处置超时升级" : "预警响应超时升级";
        String content = alert.getMessage() + "\n升级原因：" + reason;
        dispatch(tenantId, userId, alert, "ESCALATED_L" + level,
                "ALERT_ESCALATION", title, content, Set.of(AlertNotificationChannel.IN_APP.name()));
    }

    public void dispatchAlertCreated(Long tenantId, Long userId, AlertLog alert, String title, Set<String> channels) {
        dispatch(tenantId, userId, alert, "ALERT_CREATED", "ALERT", title, alert.getMessage(), channels);
    }

    public void dispatchStatusChanged(Long tenantId, Long userId, AlertLog alert, String title, String statusRemark,
                                      Set<String> channels) {
        String content = alert.getMessage();
        if (StringUtils.hasText(statusRemark)) {
            content = content + "\n处理说明：" + statusRemark.trim();
        }
        String status = StringUtils.hasText(alert.getProcessStatus())
                ? alert.getProcessStatus().trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
        dispatch(tenantId, userId, alert, "STATUS_CHANGED_" + status,
                "ALERT_STATUS", title, content, channels);
    }

    private void dispatch(Long tenantId, Long userId, AlertLog alert, String eventType,
                          String bizType, String title, String content, Set<String> channels) {
        Set<DispatchKey> sentInAppKeys = new HashSet<>();
        for (AlertNotificationSender sender : senders.stream()
                .sorted(Comparator.comparingInt(item -> item.channel().ordinal()))
                .toList()) {
            AlertNotificationChannel channel = sender.channel();
            if (!isChannelRequested(channels, channel)) {
                continue;
            }
            DispatchKey key = new DispatchKey(tenantId, alert.getId(), userId, eventType, channel);
            if (channel == AlertNotificationChannel.IN_APP) {
                Object lock = inAppLocks.computeIfAbsent(key, ignored -> new Object());
                try {
                    synchronized (lock) {
                        if (sentInAppKeys.contains(key)
                                || hasSentInAppRecord(tenantId, userId, alert.getId(), eventType)) {
                            record(tenantId, userId, alert.getId(), eventType, channel.name(), null,
                                    "SKIPPED", "DUPLICATE_IN_APP_SUPPRESSED");
                            continue;
                        }
                        if (sendAndRecord(sender, tenantId, userId, alert, eventType, bizType, title, content, channel)) {
                            sentInAppKeys.add(key);
                        }
                    }
                } finally {
                    inAppLocks.remove(key, lock);
                }
                continue;
            }
            sendAndRecord(sender, tenantId, userId, alert, eventType, bizType, title, content, channel);
        }
    }

    private boolean sendAndRecord(AlertNotificationSender sender, Long tenantId, Long userId, AlertLog alert,
                                  String eventType, String bizType, String title, String content,
                                  AlertNotificationChannel channel) {
        try {
            AlertNotificationSendResult result = sender.send(tenantId, userId, alert, eventType,
                    bizType, title, content);
            record(tenantId, userId, alert.getId(), eventType, channel.name(),
                    result.notificationId(), result.status(), result.reason());
            return "SENT".equals(result.status());
        } catch (Exception e) {
            record(tenantId, userId, alert.getId(), eventType, channel.name(), null,
                    "FAILED", e.getMessage());
            log.warn("Failed to dispatch alert notification: alertId={}, channel={}, eventType={}",
                    alert.getId(), channel.name(), eventType, e);
            return false;
        }
    }

    private boolean isChannelRequested(Set<String> channels, AlertNotificationChannel channel) {
        if (channels == null) {
            return true;
        }
        return channels.stream()
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toUpperCase(Locale.ROOT))
                .anyMatch(channel.name()::equals);
    }

    private void record(Long tenantId, Long userId, Long alertId, String eventType, String channel,
                        Long notificationId, String status, String failReason) {
        AlertNotificationSendRecord record = new AlertNotificationSendRecord();
        LocalDateTime now = LocalDateTime.now();
        record.setTenantId(tenantId);
        record.setAlertId(alertId);
        record.setEventType(eventType);
        record.setChannel(channel);
        record.setTargetUserId(userId);
        record.setBizNotificationId(notificationId);
        record.setSendStatus(status);
        record.setFailReason(failReason);
        record.setRequestedAt(now);
        record.setCompletedAt(now);
        recordMapper.insert(record);
    }

    private boolean hasSentInAppRecord(Long tenantId, Long userId, Long alertId, String eventType) {
        Long count = recordMapper.countSentInApp(tenantId, userId, alertId, eventType);
        return count != null && count > 0;
    }

    private record DispatchKey(Long tenantId, Long alertId, Long userId, String eventType,
                               AlertNotificationChannel channel) {
    }
}
