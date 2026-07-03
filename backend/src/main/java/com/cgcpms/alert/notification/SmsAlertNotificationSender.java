package com.cgcpms.alert.notification;

import com.cgcpms.alert.entity.AlertLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmsAlertNotificationSender implements AlertNotificationSender {

    private final AlertNotificationChannelProperties properties;

    @Override
    public AlertNotificationChannel channel() {
        return AlertNotificationChannel.SMS;
    }

    @Override
    public AlertNotificationSendResult send(Long tenantId, Long userId, AlertLog alert,
                                            String eventType, String bizType, String title, String content) {
        if (!properties.isConfigured(channel())) {
            return AlertNotificationSendResult.skipped("CHANNEL_NOT_CONFIGURED");
        }
        return AlertNotificationSendResult.skipped("CHANNEL_NOT_IMPLEMENTED");
    }
}
