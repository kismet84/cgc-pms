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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
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
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

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
    @Transactional
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

        // Push to SSE if user is connected
        pushToUser(userId, notification);

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
    @Transactional
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
    @Transactional
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
        String key = emitterKey(tenantId, userId);
        // Remove any existing emitter for this user in this tenant
        SseEmitter oldEmitter = emitters.remove(key);
        if (oldEmitter != null) {
            try {
                oldEmitter.complete();
            } catch (Exception ignored) {
                log.warn("Failed to clean up SSE emitter", ignored);
            }
        }

        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30-minute timeout
        emitters.put(key, emitter);

        // Cleanup on completion/error/timeout
        emitter.onCompletion(() -> {
            emitters.remove(key);
            log.debug("SSE completed for userId={}, tenantId={}", userId, tenantId);
        });
        emitter.onError(ex -> {
            emitters.remove(key);
            log.debug("SSE error for userId={}, tenantId={}", userId, tenantId, ex);
        });
        emitter.onTimeout(() -> {
            emitters.remove(key);
            log.debug("SSE timeout for userId={}, tenantId={}", userId, tenantId);
        });

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"userId\":" + userId + ",\"tenantId\":" + tenantId + "}",
                            MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitters.remove(key);
            throw new RuntimeException("Failed to send SSE connect event", e);
        }

        log.info("SSE subscribed: userId={}, tenantId={}", userId, tenantId);
        return emitter;
    }

    /**
     * Push a notification to the target user's SSE emitter.
     * Called internally by {@link #create} after persistence.
     */
    private void pushToUser(Long userId, SysNotification notification) {
        String key = emitterKey(notification.getTenantId(), userId);
        SseEmitter emitter = emitters.get(key);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(NotificationVO.fromEntity(notification), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // Remove dead or already-completed emitter
            emitters.remove(key);
            log.debug("Failed to push SSE to userId={}, tenantId={}, removed dead emitter: {}",
                    userId, notification.getTenantId(), e.getMessage());
        }
    }
}
