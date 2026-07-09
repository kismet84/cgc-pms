package com.cgcpms.alert.notification;

import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.entity.AlertNotificationSendRecord;
import com.cgcpms.alert.mapper.AlertNotificationSendRecordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertNotificationDispatcher 通知平台分发")
class AlertNotificationDispatcherTest {

    @Mock
    private AlertNotificationSendRecordMapper recordMapper;

    @Mock
    private AlertNotificationSender inAppSender;

    @Test
    @DisplayName("订阅渠道大小写和空白不应导致站内通知被静默跳过")
    void dispatchesInAppWhenSubscribedChannelHasWhitespaceAndDifferentCase() {
        AlertNotificationDispatcher dispatcher =
                new AlertNotificationDispatcher(recordMapper, List.of(inAppSender));
        AlertLog alert = alert();
        when(inAppSender.channel()).thenReturn(AlertNotificationChannel.IN_APP);
        when(inAppSender.send(eq(10L), eq(21L), eq(alert), eq("ALERT_CREATED"),
                eq("ALERT"), eq("采购逾期"), eq("采购订单逾期")))
                .thenReturn(AlertNotificationSendResult.sent(7001L));

        dispatcher.dispatchAlertCreated(10L, 21L, alert, "采购逾期", Set.of(" in_app "));

        verify(inAppSender).send(eq(10L), eq(21L), eq(alert), eq("ALERT_CREATED"),
                eq("ALERT"), eq("采购逾期"), eq("采购订单逾期"));
        ArgumentCaptor<AlertNotificationSendRecord> recordCaptor =
                ArgumentCaptor.forClass(AlertNotificationSendRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        AlertNotificationSendRecord record = recordCaptor.getValue();
        assertEquals(10L, record.getTenantId());
        assertEquals(9001L, record.getAlertId());
        assertEquals("ALERT_CREATED", record.getEventType());
        assertEquals("IN_APP", record.getChannel());
        assertEquals(21L, record.getTargetUserId());
        assertEquals(7001L, record.getBizNotificationId());
        assertEquals("SENT", record.getSendStatus());
    }

    @Test
    @DisplayName("状态变更通知渠道大小写和空白不应导致站内通知被静默跳过")
    void dispatchesStatusChangedWhenSubscribedChannelHasWhitespaceAndDifferentCase() {
        AlertNotificationDispatcher dispatcher =
                new AlertNotificationDispatcher(recordMapper, List.of(inAppSender));
        AlertLog alert = alert();
        when(inAppSender.channel()).thenReturn(AlertNotificationChannel.IN_APP);
        when(inAppSender.send(eq(10L), eq(21L), eq(alert), eq("STATUS_CHANGED"),
                eq("ALERT_STATUS"), eq("预警已归档"), eq("采购订单逾期\n处理说明：done")))
                .thenReturn(AlertNotificationSendResult.sent(7002L));

        dispatcher.dispatchStatusChanged(10L, 21L, alert, "预警已归档", " done ", Set.of(" in_app "));

        verify(inAppSender).send(eq(10L), eq(21L), eq(alert), eq("STATUS_CHANGED"),
                eq("ALERT_STATUS"), eq("预警已归档"), eq("采购订单逾期\n处理说明：done"));
        ArgumentCaptor<AlertNotificationSendRecord> recordCaptor =
                ArgumentCaptor.forClass(AlertNotificationSendRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        AlertNotificationSendRecord record = recordCaptor.getValue();
        assertEquals(10L, record.getTenantId());
        assertEquals(9001L, record.getAlertId());
        assertEquals("STATUS_CHANGED", record.getEventType());
        assertEquals("IN_APP", record.getChannel());
        assertEquals(21L, record.getTargetUserId());
        assertEquals(7002L, record.getBizNotificationId());
        assertEquals("SENT", record.getSendStatus());
    }

    private AlertLog alert() {
        AlertLog alert = new AlertLog();
        alert.setId(9001L);
        alert.setMessage("采购订单逾期");
        return alert;
    }
}
