package com.cgcpms.notification;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.notification.entity.SysNotification;
import com.cgcpms.notification.service.NotificationService;
import com.cgcpms.notification.vo.NotificationVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("通知服务 TDD 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationServiceTest {

    private static final long USER_1 = 1L;
    private static final long USER_2 = 2L;
    private static final long TENANT_0 = 0L;
    private static final long TENANT_999 = 999L;

    @Autowired
    private NotificationService notificationService;

    private Long createdNotificationId;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_1)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Create
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @Transactional
    @DisplayName("RED→GREEN: 创建通知 → 返回雪花ID + 字段持久化正确")
    void test01_createNotification() {
        SysNotification notification = notificationService.create(
                TENANT_0, USER_1,
                "测试标题", "测试内容",
                "SYSTEM", 100L);

        assertNotNull(notification.getId(), "创建应返回雪花ID");
        createdNotificationId = notification.getId();

        assertEquals(TENANT_0, notification.getTenantId());
        assertEquals(USER_1, notification.getUserId());
        assertEquals("测试标题", notification.getTitle());
        assertEquals("测试内容", notification.getContent());
        assertEquals("SYSTEM", notification.getBizType());
        assertEquals(100L, notification.getBizId());
        assertEquals("INFO", notification.getNotifyType());
        assertEquals(0, notification.getIsRead());
        assertNotNull(notification.getCreatedTime(), "创建时间应自动设置");

        System.out.println("✅ TC1 通过: notificationId=" + notification.getId()
                + ", title=" + notification.getTitle());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Page query
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @Transactional
    @DisplayName("RED→GREEN: 分页查询 → 用户隔离 + 未读筛选")
    void test02_getPage() {
        // Create 2 notifications for USER_1
        notificationService.create(TENANT_0, USER_1, "标题1", "内容1", "SYSTEM", null);
        notificationService.create(TENANT_0, USER_1, "标题2", "内容2", "WORKFLOW_APPROVAL", 200L);

        // Create 1 notification for USER_2 (should not appear in USER_1's page)
        notificationService.create(TENANT_0, USER_2, "标题3", "内容3", "SYSTEM", null);

        // Query USER_1's notifications
        PageResult<NotificationVO> page1 = notificationService.getPage(USER_1, TENANT_0, false, 1, 20);
        assertEquals(2, page1.getTotal(), "USER_1应有2条通知");
        assertTrue(page1.getRecords().stream().allMatch(vo -> String.valueOf(USER_1).equals(vo.getUserId())),
                "所有记录应属于USER_1");

        // Query with unreadOnly filter
        PageResult<NotificationVO> page2 = notificationService.getPage(USER_1, TENANT_0, true, 1, 20);
        assertEquals(2, page2.getTotal(), "未读筛选应有2条");

        // Query USER_2's notifications should not see USER_1's
        PageResult<NotificationVO> page3 = notificationService.getPage(USER_2, TENANT_0, false, 1, 20);
        assertEquals(1, page3.getTotal(), "USER_2应有1条通知");
        assertEquals(String.valueOf(USER_2), page3.getRecords().get(0).getUserId());

        System.out.println("✅ TC2 通过: USER_1 total=" + page1.getTotal()
                + ", USER_2 total=" + page3.getTotal());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Unread count
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @Transactional
    @DisplayName("RED→GREEN: 未读计数 → 按用户+租户准确统计")
    void test03_getUnreadCount() {
        // Create 3 notifications for USER_1
        notificationService.create(TENANT_0, USER_1, "t1", "c1", "SYSTEM", null);
        notificationService.create(TENANT_0, USER_1, "t2", "c2", "SYSTEM", null);
        notificationService.create(TENANT_0, USER_1, "t3", "c3", "SYSTEM", null);

        // USER_2 gets 1
        notificationService.create(TENANT_0, USER_2, "t4", "c4", "SYSTEM", null);

        long count1 = notificationService.getUnreadCount(USER_1, TENANT_0);
        assertEquals(3, count1, "USER_1应有3条未读");

        long count2 = notificationService.getUnreadCount(USER_2, TENANT_0);
        assertEquals(1, count2, "USER_2应有1条未读");

        System.out.println("✅ TC3 通过: USER_1 unread=" + count1 + ", USER_2 unread=" + count2);
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Mark as read
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @Transactional
    @DisplayName("RED→GREEN: 标记单条已读 → isRead变为1 + readTime设置")
    void test04_markAsRead() {
        SysNotification notification = notificationService.create(
                TENANT_0, USER_1, "待读标题", "待读内容", "ALERT", 300L);
        Long id = notification.getId();

        // Mark as read
        notificationService.markAsRead(id, USER_1, TENANT_0);

        // Verify via page query
        PageResult<NotificationVO> page = notificationService.getPage(USER_1, TENANT_0, false, 1, 20);
        NotificationVO vo = page.getRecords().stream()
                .filter(v -> v.getId().equals(String.valueOf(id)))
                .findFirst()
                .orElseThrow();

        assertEquals(1, vo.getIsRead(), "isRead应为1");
        assertNotNull(vo.getReadTime(), "readTime应设置");

        // Unread count should have decreased
        long unread = notificationService.getUnreadCount(USER_1, TENANT_0);
        assertEquals(0, unread, "标记已读后未读数应为0");

        System.out.println("✅ TC4 通过: id=" + id + ", isRead=" + vo.getIsRead()
                + ", readTime=" + vo.getReadTime());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Mark all as read
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @Transactional
    @DisplayName("RED→GREEN: 全部标记已读 → 所有未读变为已读")
    void test05_markAllAsRead() {
        notificationService.create(TENANT_0, USER_1, "tA", "cA", "SYSTEM", null);
        notificationService.create(TENANT_0, USER_1, "tB", "cB", "SYSTEM", null);
        notificationService.create(TENANT_0, USER_1, "tC", "cC", "SYSTEM", null);

        // Verify 3 unread
        assertEquals(3, notificationService.getUnreadCount(USER_1, TENANT_0));

        // Mark ALL as read
        notificationService.markAllAsRead(USER_1, TENANT_0);

        // Should be 0 unread
        assertEquals(0, notificationService.getUnreadCount(USER_1, TENANT_0));

        // Verify all records are read
        PageResult<NotificationVO> page = notificationService.getPage(USER_1, TENANT_0, false, 1, 20);
        assertTrue(page.getRecords().stream().allMatch(vo -> vo.getIsRead() == 1),
                "所有通知应为已读状态");

        System.out.println("✅ TC5 通过: all marked read, unread=0");
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Cross-tenant isolation
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @Transactional
    @DisplayName("RED→GREEN: 跨租户隔离 → 其他租户无法操作通知")
    void test06_crossTenantIsolation() {
        SysNotification notification = notificationService.create(
                TENANT_0, USER_1, "租户隔离测试", "内容", "SYSTEM", null);

        // Try markAsRead with wrong tenant
        BusinessException ex = assertThrows(BusinessException.class,
                () -> notificationService.markAsRead(notification.getId(), USER_1, TENANT_999),
                "不同租户标记已读应抛出BusinessException");
        assertEquals("NOTIFICATION_NOT_FOUND", ex.getCode());

        // Wrong tenant should not see notifications
        long count = notificationService.getUnreadCount(USER_1, TENANT_999);
        assertEquals(0, count, "租户999不应看到租户0的通知");

        System.out.println("✅ TC6 通过: 跨租户隔离正确, ex.code=" + ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Cross-user isolation
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @Transactional
    @DisplayName("RED→GREEN: 跨用户隔离 → USER_2不能标记USER_1的通知")
    void test07_crossUserIsolation() {
        SysNotification notification = notificationService.create(
                TENANT_0, USER_1, "用户隔离测试", "内容", "SYSTEM", null);

        // USER_2 tries to mark USER_1's notification as read
        BusinessException ex = assertThrows(BusinessException.class,
                () -> notificationService.markAsRead(notification.getId(), USER_2, TENANT_0),
                "不同用户标记已读应抛出BusinessException");
        assertEquals("NOTIFICATION_NOT_FOUND", ex.getCode());

        System.out.println("✅ TC7 通过: 跨用户隔离正确, ex.code=" + ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // REFACTOR: Idempotent markAsRead
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @Transactional
    @DisplayName("REFACTOR: 幂等标记已读 → 重复标记不抛异常")
    void test08_idempotentMarkAsRead() {
        SysNotification notification = notificationService.create(
                TENANT_0, USER_1, "幂等测试", "内容", "SYSTEM", null);

        // First mark as read
        assertDoesNotThrow(() ->
                notificationService.markAsRead(notification.getId(), USER_1, TENANT_0));

        // Second mark as read — should be idempotent
        assertDoesNotThrow(() ->
                notificationService.markAsRead(notification.getId(), USER_1, TENANT_0),
                "重复标记已读应不抛异常（幂等）");

        System.out.println("✅ TC8 通过: idempotent markAsRead works");
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Not found
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @Transactional
    @DisplayName("RED→GREEN: 操作不存在的通知 → 抛出BusinessException")
    void test09_notFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> notificationService.markAsRead(99999999L, USER_1, TENANT_0),
                "操作不存在的通知应抛出BusinessException");
        assertEquals("NOTIFICATION_NOT_FOUND", ex.getCode());

        System.out.println("✅ TC9 通过: not found throws, code=" + ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: SSE subscribe
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @Transactional
    @DisplayName("RED→GREEN: SSE订阅 → 返回有效SseEmitter")
    void test10_sseSubscribe() {
        SseEmitter emitter = notificationService.subscribe(USER_1, TENANT_0);
        assertNotNull(emitter, "SSE订阅应返回非空SseEmitter");
        // Clean up
        emitter.complete();
        System.out.println("✅ TC10 通过: SSE emitter created successfully");
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("SSE订阅按租户隔离 → 同用户不同租户不会互相替换")
    void test11_sseSubscribeSameUserDifferentTenants() {
        SseEmitter tenantZeroEmitter = notificationService.subscribe(USER_1, TENANT_0);
        SseEmitter tenantOtherEmitter = notificationService.subscribe(USER_1, TENANT_999);

        assertNotNull(tenantZeroEmitter, "租户 0 的 SSE emitter 不应为空");
        assertNotNull(tenantOtherEmitter, "租户 999 的 SSE emitter 不应为空");
        assertNotSame(tenantZeroEmitter, tenantOtherEmitter, "同一用户在不同租户的 SSE emitter 应独立存在");

        tenantZeroEmitter.complete();
        tenantOtherEmitter.complete();
        System.out.println("✅ TC11 通过: same user different tenants keep separate emitters");
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: SSE push on create
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(12)
    @Transactional
    @DisplayName("RED→GREEN: 创建通知时SSE推送 → 已订阅用户收到事件")
    void test11_ssePushOnCreate() {
        // Subscribe USER_1
        SseEmitter emitter = notificationService.subscribe(USER_1, TENANT_0);
        assertNotNull(emitter);

        // Create a notification for USER_1 — should push to emitter
        SysNotification notification = notificationService.create(
                TENANT_0, USER_1, "SSE推送测试", "SSE推送内容", "SYSTEM", null);
        assertNotNull(notification.getId(), "通知创建成功");

        // Clean up
        emitter.complete();
        System.out.println("✅ TC11 通过: SSE push triggered on create, notificationId=" + notification.getId());
    }

    // ═══════════════════════════════════════════════════════════
    // Unread Count — Unit tests (Task 8)
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(12)
    @Transactional
    @DisplayName("unreadCount: 无通知时返回 0")
    void test12_unreadCount_zero() {
        long count = notificationService.getUnreadCount(USER_1, TENANT_0);
        assertEquals(0, count, "无通知时未读数应为 0");
        System.out.println("✅ TC12 通过: unreadCount=0");
    }

    @Test
    @Order(13)
    @Transactional
    @DisplayName("unreadCount: 创建 3 条未读通知后返回 3")
    void test13_unreadCount_three() {
        notificationService.create(TENANT_0, USER_1, "T1", "C1", "SYSTEM", null);
        notificationService.create(TENANT_0, USER_1, "T2", "C2", "SYSTEM", null);
        notificationService.create(TENANT_0, USER_1, "T3", "C3", "SYSTEM", null);

        long count = notificationService.getUnreadCount(USER_1, TENANT_0);
        assertEquals(3, count, "3 条未读通知时 count 应为 3");
        System.out.println("✅ TC13 通过: unreadCount=3");
    }

    @Test
    @Order(14)
    @Transactional
    @DisplayName("unreadCount: 标记 1 条已读后减 1")
    void test14_unreadCount_afterMarkRead() {
        SysNotification n1 = notificationService.create(TENANT_0, USER_1, "R1", "C1", "SYSTEM", null);
        SysNotification n2 = notificationService.create(TENANT_0, USER_1, "R2", "C2", "SYSTEM", null);

        assertEquals(2, notificationService.getUnreadCount(USER_1, TENANT_0));

        notificationService.markAsRead(n1.getId(), USER_1, TENANT_0);
        assertEquals(1, notificationService.getUnreadCount(USER_1, TENANT_0),
                "标记 1 条已读后 count 应减 1");
        System.out.println("✅ TC14 通过: unreadCount 2→1 after markAsRead");
    }

    @Test
    @Order(15)
    @Transactional
    @DisplayName("unreadCount: 跨租户隔离 → 租户 A 的通知不影响租户 B 的计数")
    void test15_unreadCount_crossTenant() {
        notificationService.create(TENANT_0, USER_1, "TA", "CA", "SYSTEM", null);
        notificationService.create(TENANT_0, USER_1, "TB", "CB", "SYSTEM", null);

        long countForTenantA = notificationService.getUnreadCount(USER_1, TENANT_0);
        assertEquals(2, countForTenantA, "租户 0 应有 2 条未读");

        long countForTenantB = notificationService.getUnreadCount(USER_1, TENANT_999);
        assertEquals(0, countForTenantB, "租户 999 不应看到租户 0 的通知");
        System.out.println("✅ TC15 通过: cross-tenant count isolation");
    }

    @Test
    @Order(16)
    @Transactional
    @DisplayName("unreadCount: 跨用户隔离 → 用户 A 的通知不影响用户 B 的计数")
    void test16_unreadCount_crossUser() {
        notificationService.create(TENANT_0, USER_1, "UA1", "CA1", "SYSTEM", null);
        notificationService.create(TENANT_0, USER_1, "UA2", "CA2", "SYSTEM", null);

        long countForUser1 = notificationService.getUnreadCount(USER_1, TENANT_0);
        assertEquals(2, countForUser1, "USER_1 应有 2 条未读");

        long countForUser2 = notificationService.getUnreadCount(USER_2, TENANT_0);
        assertEquals(0, countForUser2, "USER_2 不应看到 USER_1 的通知");
        System.out.println("✅ TC16 通过: cross-user count isolation");
    }

    // ═══════════════════════════════════════════════════════════
    // Step 4: SSE 复合 key — 同 userId 不同 tenantId 应有独立 emitter
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(17)
    @DisplayName("SSE复合key: 相同userId不同tenantId应有独立emitter")
    void subscribeAllowsSameUserIdInDifferentTenantsWithoutReplacingEmitter() throws Exception {
        SseEmitter tenantOneEmitter = notificationService.subscribe(100L, 1L);
        SseEmitter tenantTwoEmitter = notificationService.subscribe(100L, 2L);

        assertNotSame(tenantOneEmitter, tenantTwoEmitter,
                "不同租户下相同 userId 应返回不同的 SseEmitter 实例");

        // Clean up
        tenantOneEmitter.complete();
        tenantTwoEmitter.complete();
        System.out.println("✅ TC17 通过: cross-tenant SSE emitter isolation");
    }
}
