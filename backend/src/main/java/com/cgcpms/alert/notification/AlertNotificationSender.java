package com.cgcpms.alert.notification;

import com.cgcpms.alert.entity.AlertLog;

public interface AlertNotificationSender {

    AlertNotificationChannel channel();

    AlertNotificationSendResult send(Long tenantId, Long userId, AlertLog alert,
                                     String eventType, String bizType, String title, String content);
}
