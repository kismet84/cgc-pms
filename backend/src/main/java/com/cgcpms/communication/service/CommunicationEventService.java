package com.cgcpms.communication.service;

import com.cgcpms.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class CommunicationEventService {

    private static final int MAX_CONNECTIONS = 5;
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private final ConcurrentHashMap<UserKey, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(long tenantId, long userId) {
        UserKey key = new UserKey(tenantId, userId);
        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.computeIfAbsent(key,
                ignored -> new CopyOnWriteArrayList<>());
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        synchronized (userEmitters) {
            if (userEmitters.size() >= MAX_CONNECTIONS) {
                throw new BusinessException("COMMUNICATION_CONNECTION_LIMIT", "通讯连接数量已达上限");
            }
            userEmitters.add(emitter);
        }
        Runnable cleanup = () -> remove(key, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        send(key, emitter, "connected", new CommunicationEvent("REFRESH", null, null, null));
        return emitter;
    }

    public void publish(long tenantId, List<Long> recipientIds, CommunicationEvent event) {
        recipientIds.stream().distinct().forEach(userId -> {
            UserKey key = new UserKey(tenantId, userId);
            List<SseEmitter> current = emitters.getOrDefault(key, new CopyOnWriteArrayList<>());
            current.forEach(emitter -> send(key, emitter, "communication", event));
        });
    }

    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        emitters.forEach((key, current) -> current.forEach(emitter ->
                send(key, emitter, "heartbeat", new CommunicationEvent("PING", null, null, null))));
    }

    private void send(UserKey key, SseEmitter emitter, String name, CommunicationEvent event) {
        try {
            emitter.send(SseEmitter.event().name(name).data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            remove(key, emitter);
        }
    }

    private void remove(UserKey key, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> current = emitters.get(key);
        if (current == null) return;
        current.remove(emitter);
        if (current.isEmpty()) emitters.remove(key, current);
    }

    private record UserKey(long tenantId, long userId) {}

    public record CommunicationEvent(String action, String conversationId, String messageId, String seq) {}
}
