package com.cgcpms.config;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("异步线程池配置测试")
class AsyncConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AsyncConfigurer asyncConfigurer;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void clearSubmittingContext() {
        TestUserContext.clear();
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("默认异步执行器应为统一 ThreadPoolTaskExecutor")
    void shouldRegisterThreadPoolTaskExecutorAsDefaultAsyncExecutor() {
        assertTrue(applicationContext.containsBean("taskExecutor"));

        Executor executor = applicationContext.getBean("taskExecutor", Executor.class);

        assertInstanceOf(ThreadPoolTaskExecutor.class, executor);
        assertFalse(executor instanceof SimpleAsyncTaskExecutor);
    }

    @Test
    @DisplayName("AsyncConfigurer 应返回统一线程池并暴露基础参数")
    void asyncConfigurerShouldReturnConfiguredExecutor() {
        Executor executor = asyncConfigurer.getAsyncExecutor();

        assertNotNull(executor);
        assertSame(applicationContext.getBean("taskExecutor"), executor);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(4, taskExecutor.getCorePoolSize());
        assertEquals(8, taskExecutor.getMaxPoolSize());
        assertEquals(200, taskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity()
                + taskExecutor.getThreadPoolExecutor().getQueue().size());
        assertEquals("cgc-pms-async-", taskExecutor.getThreadNamePrefix());
    }

    @Test
    @DisplayName("异步异常处理器应存在")
    void asyncUncaughtExceptionHandlerShouldExist() {
        AsyncUncaughtExceptionHandler handler = asyncConfigurer.getAsyncUncaughtExceptionHandler();
        assertNotNull(handler);
    }

    @Test
    @DisplayName("异步线程池应注册 Micrometer executor 指标")
    void shouldRegisterThreadPoolExecutorMetrics() {
        applicationContext.getBean("taskExecutor", Executor.class);

        assertNotNull(meterRegistry.find("executor.completed")
                .tag("name", "cgc.pms.async")
                .tag("executor", "taskExecutor")
                .functionCounter());
    }

    @Test
    @DisplayName("异步任务传播并清理租户、认证与 MDC 上下文")
    void shouldPropagateAndClearSubmittingContext() throws Exception {
        TestUserContext.setUser(31L, 41L, "async-user", java.util.List.of("PROJECT_MANAGER"));
        MDC.put("traceId", "trace-v07");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("async-user", "n/a", java.util.List.of()));

        ThreadPoolTaskExecutor executor = applicationContext.getBean("taskExecutor", ThreadPoolTaskExecutor.class);
        AtomicReference<UserContext.Snapshot> user = new AtomicReference<>();
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<String> principal = new AtomicReference<>();
        executor.submit(() -> {
            user.set(UserContext.capture());
            traceId.set(MDC.get("traceId"));
            principal.set(SecurityContextHolder.getContext().getAuthentication().getName());
        }).get(3, TimeUnit.SECONDS);

        assertEquals(31L, user.get().tenantId());
        assertEquals(41L, user.get().userId());
        assertEquals("trace-v07", traceId.get());
        assertEquals("async-user", principal.get());

        TestUserContext.clear();
        MDC.clear();
        SecurityContextHolder.clearContext();
        AtomicReference<UserContext.Snapshot> clearedUser = new AtomicReference<>();
        AtomicReference<String> clearedTrace = new AtomicReference<>();
        AtomicReference<Object> clearedAuthentication = new AtomicReference<>();
        executor.submit(() -> {
            clearedUser.set(UserContext.capture());
            clearedTrace.set(MDC.get("traceId"));
            clearedAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
        }).get(3, TimeUnit.SECONDS);

        assertTrue(clearedUser.get().isEmpty());
        assertEquals(null, clearedTrace.get());
        assertEquals(null, clearedAuthentication.get());
    }
}
