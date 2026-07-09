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
import static org.mockito.Mockito.times;
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

    @Test
    @DisplayName("未配置占位渠道请求应记录跳过原因且不能误记为已发送")
    void recordsUnconfiguredPlaceholderChannelsAsSkipped() {
        AlertNotificationChannelProperties properties = new AlertNotificationChannelProperties();
        AlertNotificationDispatcher dispatcher = new AlertNotificationDispatcher(recordMapper, List.of(
                new EmailAlertNotificationSender(properties),
                new WechatAlertNotificationSender(properties),
                new SmsAlertNotificationSender(properties)
        ));
        AlertLog alert = alert();

        dispatcher.dispatchAlertCreated(10L, 21L, alert, "采购逾期", Set.of("EMAIL", "WECHAT", "SMS"));

        ArgumentCaptor<AlertNotificationSendRecord> recordCaptor =
                ArgumentCaptor.forClass(AlertNotificationSendRecord.class);
        verify(recordMapper, times(3)).insert(recordCaptor.capture());
        List<AlertNotificationSendRecord> records = recordCaptor.getAllValues();
        assertSkipped(records.get(0), "EMAIL", "CHANNEL_NOT_CONFIGURED");
        assertSkipped(records.get(1), "WECHAT", "CHANNEL_NOT_CONFIGURED");
        assertSkipped(records.get(2), "SMS", "CHANNEL_NOT_CONFIGURED");
    }

    @Test
    @DisplayName("已配置但未实现的占位渠道仍应记录跳过原因且不能误记为已发送")
    void recordsConfiguredPlaceholderChannelAsNotImplemented() {
        AlertNotificationChannelProperties properties = new AlertNotificationChannelProperties() {
            @Override
            public boolean isConfigured(AlertNotificationChannel channel) {
                return channel == AlertNotificationChannel.EMAIL;
            }
        };
        AlertNotificationDispatcher dispatcher = new AlertNotificationDispatcher(recordMapper,
                List.of(new EmailAlertNotificationSender(properties)));
        AlertLog alert = alert();

        dispatcher.dispatchAlertCreated(10L, 21L, alert, "采购逾期", Set.of("EMAIL"));

        ArgumentCaptor<AlertNotificationSendRecord> recordCaptor =
                ArgumentCaptor.forClass(AlertNotificationSendRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        assertSkipped(recordCaptor.getValue(), "EMAIL", "CHANNEL_NOT_IMPLEMENTED");
    }

    @Test
    @DisplayName("同一次分发内同告警同用户同事件重复站内通知应记录为跳过")
    void skipsDuplicateInAppWithinSameDispatch() {
        AlertNotificationSender duplicateInAppSender = org.mockito.Mockito.mock(AlertNotificationSender.class);
        AlertNotificationDispatcher dispatcher =
                new AlertNotificationDispatcher(recordMapper, List.of(inAppSender, duplicateInAppSender));
        AlertLog alert = alert();
        when(inAppSender.channel()).thenReturn(AlertNotificationChannel.IN_APP);
        when(duplicateInAppSender.channel()).thenReturn(AlertNotificationChannel.IN_APP);
        when(inAppSender.send(eq(10L), eq(21L), eq(alert), eq("ALERT_CREATED"),
                eq("ALERT"), eq("采购逾期"), eq("采购订单逾期")))
                .thenReturn(AlertNotificationSendResult.sent(7001L));

        dispatcher.dispatchAlertCreated(10L, 21L, alert, "采购逾期", Set.of("IN_APP"));

        verify(inAppSender).send(eq(10L), eq(21L), eq(alert), eq("ALERT_CREATED"),
                eq("ALERT"), eq("采购逾期"), eq("采购订单逾期"));
        verify(duplicateInAppSender, times(0)).send(any(), any(), any(), any(), any(), any(), any());
        ArgumentCaptor<AlertNotificationSendRecord> recordCaptor =
                ArgumentCaptor.forClass(AlertNotificationSendRecord.class);
        verify(recordMapper, times(2)).insert(recordCaptor.capture());
        List<AlertNotificationSendRecord> records = recordCaptor.getAllValues();
        assertEquals("SENT", records.get(0).getSendStatus());
        assertEquals(7001L, records.get(0).getBizNotificationId());
        assertEquals("SKIPPED", records.get(1).getSendStatus());
        assertEquals("DUPLICATE_IN_APP_SUPPRESSED", records.get(1).getFailReason());
        assertEquals("ALERT_CREATED", records.get(1).getEventType());
        assertEquals("IN_APP", records.get(1).getChannel());
        assertEquals(9001L, records.get(1).getAlertId());
        assertEquals(21L, records.get(1).getTargetUserId());
    }

    @Test
    @DisplayName("连续两次同告警同用户同事件站内通知应抑制第二次有效通知")
    void skipsDuplicateInAppAcrossSequentialDispatches() {
        AlertNotificationDispatcher dispatcher =
                new AlertNotificationDispatcher(recordMapper, List.of(inAppSender));
        AlertLog alert = alert();
        when(inAppSender.channel()).thenReturn(AlertNotificationChannel.IN_APP);
        when(inAppSender.send(eq(10L), eq(21L), eq(alert), eq("ALERT_CREATED"),
                eq("ALERT"), eq("采购逾期"), eq("采购订单逾期")))
                .thenReturn(AlertNotificationSendResult.sent(7001L));
        when(recordMapper.selectCount(any())).thenReturn(0L, 1L);

        dispatcher.dispatchAlertCreated(10L, 21L, alert, "采购逾期", Set.of("IN_APP"));
        dispatcher.dispatchAlertCreated(10L, 21L, alert, "采购逾期", Set.of("IN_APP"));

        verify(inAppSender).send(eq(10L), eq(21L), eq(alert), eq("ALERT_CREATED"),
                eq("ALERT"), eq("采购逾期"), eq("采购订单逾期"));
        ArgumentCaptor<AlertNotificationSendRecord> recordCaptor =
                ArgumentCaptor.forClass(AlertNotificationSendRecord.class);
        verify(recordMapper, times(2)).insert(recordCaptor.capture());
        List<AlertNotificationSendRecord> records = recordCaptor.getAllValues();
        assertEquals("SENT", records.get(0).getSendStatus());
        assertEquals(7001L, records.get(0).getBizNotificationId());
        assertEquals("SKIPPED", records.get(1).getSendStatus());
        assertEquals("DUPLICATE_IN_APP_SUPPRESSED", records.get(1).getFailReason());
        assertEquals("ALERT_CREATED", records.get(1).getEventType());
        assertEquals("IN_APP", records.get(1).getChannel());
        assertEquals(9001L, records.get(1).getAlertId());
        assertEquals(21L, records.get(1).getTargetUserId());
    }

    @Test
    @DisplayName("不同事件类型和不同告警不应被站内通知抑制合并")
    void doesNotSuppressDifferentEventTypeOrAlert() {
        AlertNotificationDispatcher dispatcher =
                new AlertNotificationDispatcher(recordMapper, List.of(inAppSender));
        AlertLog alert = alert();
        AlertLog anotherAlert = alert();
        anotherAlert.setId(9002L);
        when(inAppSender.channel()).thenReturn(AlertNotificationChannel.IN_APP);
        when(inAppSender.send(eq(10L), eq(21L), eq(alert), eq("ALERT_CREATED"),
                eq("ALERT"), eq("采购逾期"), eq("采购订单逾期")))
                .thenReturn(AlertNotificationSendResult.sent(7001L));
        when(inAppSender.send(eq(10L), eq(21L), eq(alert), eq("STATUS_CHANGED"),
                eq("ALERT_STATUS"), eq("预警已归档"), eq("采购订单逾期")))
                .thenReturn(AlertNotificationSendResult.sent(7002L));
        when(inAppSender.send(eq(10L), eq(21L), eq(anotherAlert), eq("ALERT_CREATED"),
                eq("ALERT"), eq("另一个采购逾期"), eq("采购订单逾期")))
                .thenReturn(AlertNotificationSendResult.sent(7003L));

        dispatcher.dispatchAlertCreated(10L, 21L, alert, "采购逾期", Set.of("IN_APP"));
        dispatcher.dispatchStatusChanged(10L, 21L, alert, "预警已归档", null, Set.of("IN_APP"));
        dispatcher.dispatchAlertCreated(10L, 21L, anotherAlert, "另一个采购逾期", Set.of("IN_APP"));

        ArgumentCaptor<AlertNotificationSendRecord> recordCaptor =
                ArgumentCaptor.forClass(AlertNotificationSendRecord.class);
        verify(recordMapper, times(3)).insert(recordCaptor.capture());
        List<AlertNotificationSendRecord> records = recordCaptor.getAllValues();
        assertEquals("SENT", records.get(0).getSendStatus());
        assertEquals("ALERT_CREATED", records.get(0).getEventType());
        assertEquals(9001L, records.get(0).getAlertId());
        assertEquals("SENT", records.get(1).getSendStatus());
        assertEquals("STATUS_CHANGED", records.get(1).getEventType());
        assertEquals(9001L, records.get(1).getAlertId());
        assertEquals("SENT", records.get(2).getSendStatus());
        assertEquals("ALERT_CREATED", records.get(2).getEventType());
        assertEquals(9002L, records.get(2).getAlertId());
    }

    private void assertSkipped(AlertNotificationSendRecord record, String channel, String failReason) {
        assertEquals(10L, record.getTenantId());
        assertEquals(9001L, record.getAlertId());
        assertEquals("ALERT_CREATED", record.getEventType());
        assertEquals(channel, record.getChannel());
        assertEquals(21L, record.getTargetUserId());
        assertEquals("SKIPPED", record.getSendStatus());
        assertEquals(failReason, record.getFailReason());
    }

    private AlertLog alert() {
        AlertLog alert = new AlertLog();
        alert.setId(9001L);
        alert.setMessage("采购订单逾期");
        return alert;
    }
}
