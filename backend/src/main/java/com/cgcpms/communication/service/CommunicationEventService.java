package com.cgcpms.communication.service;

import com.cgcpms.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CommunicationEventService {

    private static final int MAX_CONNECTIONS = 5;
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private final ConcurrentHashMap<UserKey, ConcurrentHashMap<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(long tenantId, long userId) {
        return subscribe(tenantId, userId, UUID.randomUUID().toString());
    }

    public SseEmitter subscribe(long tenantId, long userId, String requestedClientId) {
        UserKey key = new UserKey(tenantId, userId);
        String clientId = normalizeClientId(requestedClientId);
        ConcurrentHashMap<String, SseEmitter> userEmitters = emitters.computeIfAbsent(key,
                ignored -> new ConcurrentHashMap<>());
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Runnable cleanup = () -> remove(key, clientId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        SseEmitter replaced;
        synchronized (userEmitters) {
            if (!userEmitters.containsKey(clientId) && userEmitters.size() >= MAX_CONNECTIONS) {
                throw new BusinessException("COMMUNICATION_CONNECTION_LIMIT", "通讯连接数量已达上限");
            }
            replaced = userEmitters.put(clientId, emitter);
        }
        if (replaced != null) {
            try {
                replaced.complete();
            } catch (IllegalStateException exception) {
                log.debug("SSE replacement already completed for tenant {} user {}", tenantId, userId);
            }
        }
        send(key, clientId, emitter, "connected", new CommunicationEvent("REFRESH", null, null, null));
        return emitter;
    }

    public void publish(long tenantId, List<Long> recipientIds, CommunicationEvent event) {
        recipientIds.stream().distinct().forEach(userId -> {
            UserKey key = new UserKey(tenantId, userId);
            Map<String, SseEmitter> current = emitters.getOrDefault(key, new ConcurrentHashMap<>());
            current.forEach((clientId, emitter) -> send(key, clientId, emitter, "communication", event));
        });
    }

    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        emitters.forEach((key, current) -> current.forEach((clientId, emitter) ->
                send(key, clientId, emitter, "heartbeat", new CommunicationEvent("PING", null, null, null))));
    }

    private void send(UserKey key, String clientId, SseEmitter emitter, String name, CommunicationEvent event) {
        try {
            emitter.send(SseEmitter.event().name(name).data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            remove(key, clientId, emitter);
        }
    }

    private void remove(UserKey key, String clientId, SseEmitter emitter) {
        ConcurrentHashMap<String, SseEmitter> current = emitters.get(key);
        if (current == null) return;
        current.remove(clientId, emitter);
        if (current.isEmpty()) emitters.remove(key, current);
    }

    private String normalizeClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) return UUID.randomUUID().toString();
        String normalized = clientId.trim();
        if (normalized.length() > 64 || !normalized.matches("[A-Za-z0-9._-]+")) {
            throw new BusinessException("COMMUNICATION_CLIENT_ID_INVALID", "通讯客户端标识不合法");
        }
        return normalized;
    }

    private record UserKey(long tenantId, long userId) {}

    public record CommunicationEvent(String action, String conversationId, String messageId, String seq) {}
}
