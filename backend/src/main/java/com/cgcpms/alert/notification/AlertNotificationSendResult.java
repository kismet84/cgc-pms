package com.cgcpms.alert.notification;

public record AlertNotificationSendResult(String status, Long notificationId, String reason) {

    public static AlertNotificationSendResult sent(Long notificationId) {
        return new AlertNotificationSendResult("SENT", notificationId, null);
    }

    public static AlertNotificationSendResult skipped(String reason) {
        return new AlertNotificationSendResult("SKIPPED", null, reason);
    }
}
