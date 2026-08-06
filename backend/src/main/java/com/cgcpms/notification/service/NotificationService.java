package com.cgcpms.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.notification.entity.SysNotification;
import com.cgcpms.notification.mapper.SysNotificationMapper;
import com.cgcpms.notification.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notification service — all methods take tenantId/userId EXPLICITLY.
 * Never reads from UserContext; safe for SSE push and scheduled tasks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SysNotificationMapper notificationMapper;

    /**
     * Per-user SSE emitter map. Keyed by "tenantId:userId" composite key
     * for tenant isolation, used to push real-time
     * notifications to connected SSE clients.
     */
    private final Map<String, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Value("${notification.multi-client.enabled:true}")
    private boolean multiClientEnabled = true;

    @Value("${notification.multi-client.max-connections-per-user:5}")
    private int maxConnectionsPerUser = 5;

    private String emitterKey(Long tenantId, Long userId) {
        return tenantId + ":" + userId;
    }

    // ──────────────────────────────────────────────
    // CRUD
    // ──────────────────────────────────────────────

    /**
     * Create a notification. ALL params EXPLICIT (no UserContext read).
     * After persisting, pushes the notification to the target user's SSE emitter if connected.
     */
    @Transactional(rollbackFor = Exception.class)
    public SysNotification create(Long tenantId, Long userId, String title, String content,
                                   String bizType, Long bizId) {
        SysNotification notification = new SysNotification();
        notification.setTenantId(tenantId);
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        notification.setNotifyType("INFO");
        notification.setIsRead(0);
        notification.setCreatedTime(LocalDateTime.now());

        notificationMapper.insert(notification);
        log.debug("Notification created: id={}, userId={}, tenantId={}, bizType={}",
                notification.getId(), userId, tenantId, bizType);

        pushAfterCommit(notification);

        return notification;
    }

    /**
     * Paginated query for notifications of a specific user in a tenant.
     *
     * @param unreadOnly if true, only return unread notifications
     */
    public PageResult<NotificationVO> getPage(Long userId, Long tenantId,
                                               Boolean unreadOnly,
                                               int pageNo, int pageSize) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getTenantId, tenantId);
        wrapper.eq(SysNotification::getUserId, userId);
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.eq(SysNotification::getIsRead, 0);
        }
        wrapper.orderByDesc(SysNotification::getCreatedTime);

        IPage<SysNotification> page = notificationMapper.selectPage(
                new Page<>(pageNo, pageSize), wrapper);

        IPage<NotificationVO> voPage = page.convert(NotificationVO::fromEntity);
        return PageResult.of(voPage);
    }

    /**
     * Get unread notification count for a specific user in a tenant.
     */
    public long getUnreadCount(Long userId, Long tenantId) {
        log.debug("getUnreadCount called: userId={}, tenantId={}", userId, tenantId);
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getTenantId, tenantId);
        wrapper.eq(SysNotification::getUserId, userId);
        wrapper.eq(SysNotification::getIsRead, 0);
        return notificationMapper.selectCount(wrapper);
    }

    /**
     * Mark a single notification as read. Validates tenant ownership.
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long id, Long userId, Long tenantId) {
        SysNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException("NOTIFICATION_NOT_FOUND", "通知不存在");
        }
        if (!Objects.equals(notification.getTenantId(), tenantId)) {
            throw new BusinessException("NOTIFICATION_NOT_FOUND", "通知不存在");
        }
        if (!Objects.equals(notification.getUserId(), userId)) {
            throw new BusinessException("NOTIFICATION_NOT_FOUND", "通知不存在");
        }
        if (notification.getIsRead() != null && notification.getIsRead() == 1) {
            return; // Already read, idempotent
        }
        notification.setIsRead(1);
        notification.setReadTime(LocalDateTime.now());
        notificationMapper.updateById(notification);
    }

    /**
     * Mark all unread notifications as read for a specific user in a tenant.
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId, Long tenantId) {
        SysNotification update = new SysNotification();
        update.setIsRead(1);
        update.setReadTime(LocalDateTime.now());

        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getTenantId, tenantId);
        wrapper.eq(SysNotification::getUserId, userId);
        wrapper.eq(SysNotification::getIsRead, 0);

        notificationMapper.update(update, wrapper);
        log.debug("Marked all notifications as read: userId={}, tenantId={}", userId, tenantId);
    }

    // ──────────────────────────────────────────────
    // SSE
    // ──────────────────────────────────────────────

    /**
     * Subscribe to SSE stream for a specific user. The emitter is stored in a
     * ConcurrentHashMap and removed on completion/error/timeout.
     */
    public SseEmitter subscribe(Long userId, Long tenantId) {
        return subscribe(userId, tenantId, UUID.randomUUID().toString());
    }

    public SseEmitter subscribe(Long userId, Long tenantId, String requestedClientId) {
        String key = emitterKey(tenantId, userId);
        String clientId = multiClientEnabled ? normalizeClientId(requestedClientId) : "legacy";
        Map<String, SseEmitter> userEmitters = emitters.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        SseEmitter emitter = newEmitter();
        SseEmitter replaced;
        synchronized (userEmitters) {
            if (!userEmitters.containsKey(clientId) && userEmitters.size() >= maxConnectionsPerUser) {
                throw new BusinessException("SSE_CONNECTION_LIMIT", "实时通知连接数已达上限");
            }
            replaced = userEmitters.put(clientId, emitter);
        }
        if (replaced != null) {
            try {
                replaced.complete();
            } catch (Exception cleanupFailure) {
                log.debug("Failed to complete replaced SSE emitter: tenantId={}, userId={}, clientId={}",
                        tenantId, userId, clientId, cleanupFailure);
            }
        }

        // Cleanup on completion/error/timeout
        emitter.onCompletion(() -> {
            removeEmitter(key, clientId, emitter);
            log.debug("SSE completed for userId={}, tenantId={}, clientId={}", userId, tenantId, clientId);
        });
        emitter.onError(ex -> {
            removeEmitter(key, clientId, emitter);
            log.debug("SSE error for userId={}, tenantId={}, clientId={}", userId, tenantId, clientId, ex);
        });
        emitter.onTimeout(() -> {
            removeEmitter(key, clientId, emitter);
            log.debug("SSE timeout for userId={}, tenantId={}, clientId={}", userId, tenantId, clientId);
        });

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"userId\":" + userId + ",\"tenantId\":" + tenantId
                                    + ",\"clientId\":\"" + clientId + "\"}",
                            MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            removeEmitter(key, clientId, emitter);
            throw new RuntimeException("Failed to send SSE connect event", e);
        }

        log.info("SSE subscribed: userId={}, tenantId={}, clientId={}, connections={}",
                userId, tenantId, clientId, activeConnectionCount(tenantId, userId));
        return emitter;
    }

    protected SseEmitter newEmitter() {
        return new SseEmitter(30 * 60 * 1000L);
    }

    public int activeConnectionCount(Long tenantId, Long userId) {
        Map<String, SseEmitter> current = emitters.get(emitterKey(tenantId, userId));
        return current == null ? 0 : current.size();
    }

    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        emitters.forEach((key, current) -> current.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("{}", MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException sendFailure) {
                removeEmitter(key, clientId, emitter);
            }
        }));
    }

    /**
     * Push a notification to the target user's SSE emitter.
     * Called internally by {@link #create} after persistence.
     */
    private void pushAfterCommit(SysNotification notification) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pushToUser(notification);
                }
            });
        } else {
            pushToUser(notification);
        }
    }

    private void pushToUser(SysNotification notification) {
        String key = emitterKey(notification.getTenantId(), notification.getUserId());
        Map<String, SseEmitter> userEmitters = emitters.get(key);
        if (userEmitters == null) return;
        userEmitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(notification.getId()))
                        .name("notification")
                        .data(NotificationVO.fromEntity(notification), MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException sendFailure) {
                removeEmitter(key, clientId, emitter);
                log.debug("Notification SSE fan-out failed: notificationId={}, tenantId={}, userId={}, clientId={}, errorType={}",
                        notification.getId(), notification.getTenantId(), notification.getUserId(), clientId,
                        sendFailure.getClass().getSimpleName());
            }
        });
    }

    private void removeEmitter(String key, String clientId, SseEmitter emitter) {
        Map<String, SseEmitter> current = emitters.get(key);
        if (current == null) return;
        current.remove(clientId, emitter);
        if (current.isEmpty()) emitters.remove(key, current);
    }

    private String normalizeClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) return UUID.randomUUID().toString();
        String normalized = clientId.trim();
        if (normalized.length() > 64 || !normalized.matches("[A-Za-z0-9._-]+")) {
            throw new BusinessException("SSE_CLIENT_ID_INVALID", "实时通知客户端标识不合法");
        }
        return normalized;
    }
}
