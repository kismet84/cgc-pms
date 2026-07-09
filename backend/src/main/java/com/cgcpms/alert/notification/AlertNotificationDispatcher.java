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
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotificationDispatcher {

    private final AlertNotificationSendRecordMapper recordMapper;
    private final List<AlertNotificationSender> senders;

    public void dispatchAlertCreated(Long tenantId, Long userId, AlertLog alert, String title) {
        dispatchAlertCreated(tenantId, userId, alert, title, Set.of(AlertNotificationChannel.IN_APP.name()));
    }

    public void dispatchStatusChanged(Long tenantId, Long userId, AlertLog alert, String title, String statusRemark) {
        dispatchStatusChanged(tenantId, userId, alert, title, statusRemark, Set.of(AlertNotificationChannel.IN_APP.name()));
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
        dispatch(tenantId, userId, alert, "STATUS_CHANGED", "ALERT_STATUS", title, content, channels);
    }

    private void dispatch(Long tenantId, Long userId, AlertLog alert, String eventType,
                          String bizType, String title, String content, Set<String> channels) {
        for (AlertNotificationSender sender : senders.stream()
                .sorted(Comparator.comparingInt(item -> item.channel().ordinal()))
                .toList()) {
            if (!isChannelRequested(channels, sender.channel())) {
                continue;
            }
            try {
                AlertNotificationSendResult result = sender.send(tenantId, userId, alert, eventType,
                        bizType, title, content);
                record(tenantId, userId, alert.getId(), eventType, sender.channel().name(),
                        result.notificationId(), result.status(), result.reason());
            } catch (Exception e) {
                record(tenantId, userId, alert.getId(), eventType, sender.channel().name(), null,
                        "FAILED", e.getMessage());
                log.warn("Failed to dispatch alert notification: alertId={}, channel={}, eventType={}",
                        alert.getId(), sender.channel().name(), eventType, e);
            }
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
}
