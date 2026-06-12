package com.cgcpms.notification.controller;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.notification.entity.SysNotification;
import com.cgcpms.notification.service.NotificationService;
import com.cgcpms.notification.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Notification REST controller.
 *
 * <p>SSE endpoint {@code /api/notifications/stream} uses HttpOnly cookie auth
 * (JwtAuthenticationFilter applies on the servlet path) and pushes new
 * notifications to the authenticated user in real time.</p>
 */
@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // ──────────────────────────────────────────────
    // CRUD
    // ──────────────────────────────────────────────

    /**
     * List notifications for the current user, paginated.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('notification:view') or hasRole('ADMIN')")
    public ApiResponse<PageResult<NotificationVO>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Boolean unreadOnly) {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        PageResult<NotificationVO> page = notificationService.getPage(
                userId, tenantId, unreadOnly, pageNo, pageSize);
        return ApiResponse.success(page);
    }

    /**
     * Get unread notification count for the current user.
     */
    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notification:view') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Long>> unreadCount() {
        try {
            Long userId = UserContext.getCurrentUserId();
            Long tenantId = UserContext.getCurrentTenantId();
            long count = notificationService.getUnreadCount(userId, tenantId);
            return ApiResponse.success(Map.of("count", count));
        } catch (Exception e) {
            log.error("unreadCount failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Mark a single notification as read.
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notification:edit') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> markAsRead(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        notificationService.markAsRead(id, userId, tenantId);
        return ApiResponse.success(Map.of("id", String.valueOf(id), "read", true));
    }

    /**
     * Mark all unread notifications as read for the current user.
     */
    @PutMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:edit') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> markAllAsRead() {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        notificationService.markAllAsRead(userId, tenantId);
        return ApiResponse.success(Map.of("userId", String.valueOf(userId), "allRead", true));
    }

    // ──────────────────────────────────────────────
    // SSE stream
    // ──────────────────────────────────────────────

    /**
     * Subscribe to real-time notification stream via Server-Sent Events.
     *
     * <p>Authenticated via HttpOnly cookie (JwtAuthenticationFilter applies on
     * the same servlet path). The stream pushes two event types:</p>
     * <ul>
     *   <li>{@code connected} — sent immediately on subscription</li>
     *   <li>{@code notification} — sent when a new notification is created for this user</li>
     * </ul>
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        return notificationService.subscribe(userId, tenantId);
    }
}
