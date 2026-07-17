package com.cgcpms.alert.service;

import com.cgcpms.alert.entity.AlertLifecycleEvent;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLifecycleEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlertLifecycleService {
    private final AlertLifecycleEventMapper eventMapper;
    private final ObjectMapper objectMapper;

    public void recordCreated(AlertLog alert) {
        initialize(alert);
        record(alert, "CREATED", null, alert.getProcessStatus(), alert.getCreatedBy(), "预警生成");
    }

    public void initialize(AlertLog alert) {
        if (alert.getResponseDueAt() == null) {
            LocalDateTime base = alert.getTriggeredAt() == null ? LocalDateTime.now() : alert.getTriggeredAt();
            alert.setResponseDueAt(base.plusHours(slaHours(alert.getSeverity())));
        }
        if (alert.getResolutionDueAt() == null) {
            LocalDateTime base = alert.getTriggeredAt() == null ? LocalDateTime.now() : alert.getTriggeredAt();
            alert.setResolutionDueAt(base.plusHours(resolutionSlaHours(alert.getSeverity())));
        }
        if (alert.getEscalationLevel() == null) alert.setEscalationLevel(0);
        if (alert.getVersion() == null) alert.setVersion(0);
    }

    public void record(AlertLog alert, String eventType, String fromStatus, String toStatus,
                       Long operatorId, String remark) {
        try {
            LocalDateTime occurredAt = LocalDateTime.now();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("alertId", alert.getId());
            payload.put("eventType", eventType);
            payload.put("fromStatus", fromStatus);
            payload.put("toStatus", toStatus);
            payload.put("operatorId", operatorId);
            payload.put("occurredAt", occurredAt.toString());
            payload.put("remark", remark);
            String json = objectMapper.writeValueAsString(payload);

            AlertLifecycleEvent event = new AlertLifecycleEvent();
            event.setTenantId(alert.getTenantId());
            event.setAlertId(alert.getId());
            event.setEventType(eventType);
            event.setFromStatus(fromStatus);
            event.setToStatus(toStatus);
            event.setOperatorId(operatorId);
            event.setRemark(remark);
            event.setOccurredAt(occurredAt);
            event.setPayloadJson(json);
            event.setPayloadHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8))));
            eventMapper.insert(event);
        } catch (Exception e) {
            throw new IllegalStateException("记录预警生命周期事件失败", e);
        }
    }

    private long slaHours(String severity) {
        if ("HIGH".equals(severity)) return 4;
        if ("LOW".equals(severity)) return 72;
        return 24;
    }

    private long resolutionSlaHours(String severity) {
        if ("HIGH".equals(severity)) return 24;
        if ("LOW".equals(severity)) return 168;
        return 72;
    }
}
