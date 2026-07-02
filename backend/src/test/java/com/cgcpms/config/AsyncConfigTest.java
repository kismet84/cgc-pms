package com.cgcpms.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.Executor;

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
}
