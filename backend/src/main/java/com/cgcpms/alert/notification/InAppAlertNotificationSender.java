package com.cgcpms.alert.notification;

import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.notification.entity.SysNotification;
import com.cgcpms.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InAppAlertNotificationSender implements AlertNotificationSender {

    private final NotificationService notificationService;

    @Override
    public AlertNotificationChannel channel() {
        return AlertNotificationChannel.IN_APP;
    }

    @Override
    public AlertNotificationSendResult send(Long tenantId, Long userId, AlertLog alert,
                                            String eventType, String bizType, String title, String content) {
        SysNotification notification = notificationService.create(tenantId, userId, title, content,
                bizType, alert.getId());
        return AlertNotificationSendResult.sent(notification.getId());
    }
}
