package com.cgcpms.alert.notification;

import org.springframework.stereotype.Component;

@Component
public class AlertNotificationChannelProperties {

    public boolean isConfigured(AlertNotificationChannel channel) {
        return channel == AlertNotificationChannel.IN_APP;
    }
}
