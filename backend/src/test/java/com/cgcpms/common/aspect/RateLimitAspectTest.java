package com.cgcpms.common.aspect;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.ratelimit.FallbackRateLimitCounterStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("local")
@DisplayName("RateLimitAspect — 多维限流测试")
class RateLimitAspectTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FallbackRateLimitCounterStore counterStore;

    @BeforeEach
    void setUp() {
        counterStore.clear();
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    /* =====================================================================
     * 1. USER dimension — same IP different users isolated
     * ===================================================================== */

    @Test
    @DisplayName("USER 维度 — 用户A用满配额后，用户B仍可正常请求")
    void testDifferentUsersIsolated() throws Exception {
        // User A (id=1) exhausts their quota
        TestUserContext.setAdmin(TestUserContext.TENANT_0, 1L);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/test-rl/user-limited")
                    .servletPath("/test-rl/user-limited")
                    .contextPath("/api")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
        // Next request from User A should be rate-limited
        mockMvc.perform(get("/api/test-rl/user-limited")
                        .servletPath("/test-rl/user-limited")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());

        TestUserContext.clear();

        // User B (id=2) should still succeed
        TestUserContext.setUser(TestUserContext.TENANT_0, 2L, "user2", java.util.List.of("USER"));
        mockMvc.perform(get("/api/test-rl/user-limited")
                        .servletPath("/test-rl/user-limited")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    /* =====================================================================
     * 2. TENANT dimension — shared quota
     * ===================================================================== */

    @Test
    @DisplayName("TENANT 维度 — 租户内共享配额")
    void testSameTenantSharedQuota() throws Exception {
        TestUserContext.setAdmin(1L, 1L);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/test-rl/tenant-limited")
                    .servletPath("/test-rl/tenant-limited")
                    .contextPath("/api")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
        TestUserContext.clear();

        TestUserContext.setUser(1L, 2L, "user2", java.util.List.of("USER"));
        mockMvc.perform(get("/api/test-rl/tenant-limited")
                        .servletPath("/test-rl/tenant-limited")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("TENANT 维度 — 不同租户额度隔离")
    void testDifferentTenantsIsolated() throws Exception {
        TestUserContext.setAdmin(1L, 1L);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/test-rl/tenant-limited")
                    .servletPath("/test-rl/tenant-limited")
                    .contextPath("/api")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
        TestUserContext.clear();

        TestUserContext.setAdmin(2L, 3L);
        mockMvc.perform(get("/api/test-rl/tenant-limited")
                        .servletPath("/test-rl/tenant-limited")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    /* =====================================================================
     * 3. Exceed threshold → 429
     * ===================================================================== */

    @Test
    @DisplayName("IP 维度默认限流，超过限制后返回 429")
    void testIpRateLimitExceeded() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/test-rl/ip-limited")
                    .servletPath("/test-rl/ip-limited")
                    .contextPath("/api")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/test-rl/ip-limited")
                        .servletPath("/test-rl/ip-limited")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /* =====================================================================
     * 4. Window recovery
     * ===================================================================== */

    @Test
    @DisplayName("窗口过期后计数器重置，请求恢复正常")
    void testWindowRecovery() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/test-rl/short-window")
                    .servletPath("/test-rl/short-window")
                    .contextPath("/api")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/test-rl/short-window")
                        .servletPath("/test-rl/short-window")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());

        Thread.sleep(1_200);

        mockMvc.perform(get("/api/test-rl/short-window")
                        .servletPath("/test-rl/short-window")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    /* =====================================================================
     * 5. Fallback counter store
     * ===================================================================== */

    @Test
    @DisplayName("在 local profile 下使用 Guava 降级存储，非 null")
    void testFallbackStoreActive() {
        assertTrue(counterStore != null, "Fallback store should be autowired");
    }

    @Test
    @DisplayName("increment 返回递增计数值")
    void testIncrementReturnsIncrementingCounts() {
        long c1 = counterStore.increment("test:ip:127.0.0.1", 60);
        long c2 = counterStore.increment("test:ip:127.0.0.1", 60);
        long c3 = counterStore.increment("test:ip:127.0.0.1", 60);
        assertEquals(1, c1);
        assertEquals(2, c2);
        assertEquals(3, c3);
    }

    @Test
    @DisplayName("IP_AND_ACCOUNT 维度 — IP相同账号不同时隔离")
    void testIpAndAccountIsolation() throws Exception {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, 1L);
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/test-rl/ip-account-limited")
                    .servletPath("/test-rl/ip-account-limited")
                    .contextPath("/api")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/test-rl/ip-account-limited")
                        .servletPath("/test-rl/ip-account-limited")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());

        TestUserContext.clear();

        TestUserContext.setUser(TestUserContext.TENANT_0, 2L, "user2", java.util.List.of("USER"));
        mockMvc.perform(get("/api/test-rl/ip-account-limited")
                        .servletPath("/test-rl/ip-account-limited")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("多线程并发限流 — 计数器准确")
    void testConcurrentRateLimiting() throws Exception {
        int threads = 10;
        int limit = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    mockMvc.perform(get("/api/test-rl/concurrency-limited")
                            .servletPath("/test-rl/concurrency-limited")
                            .contextPath("/api")
                            .contentType(MediaType.APPLICATION_JSON))
                            .andDo(result -> {
                                if (result.getResponse().getStatus() == 200) {
                                    successCount.incrementAndGet();
                                } else {
                                    blockedCount.incrementAndGet();
                                }
                            });
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        assertEquals(limit, successCount.get(), "Exactly " + limit + " requests should succeed");
        assertEquals(threads - limit, blockedCount.get(), "Remaining should be blocked");
    }
}
