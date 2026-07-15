package com.cgcpms.config;

import com.cgcpms.auth.context.UserContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    @Value("${app.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${app.async.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${app.async.queue-capacity:200}")
    private int queueCapacity;

    @Value("${app.async.thread-name-prefix:cgc-pms-async-}")
    private String threadNamePrefix;

    public AsyncConfig(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(this::decorateWithSubmittingContext);
        executor.initialize();
        meterRegistryProvider.ifAvailable(meterRegistry -> ExecutorServiceMetrics.monitor(
                meterRegistry,
                executor.getThreadPoolExecutor(),
                "cgc.pms.async",
                Tags.of("executor", "taskExecutor")));
        return executor;
    }

    private Runnable decorateWithSubmittingContext(Runnable task) {
        UserContext.Snapshot submittingUser = UserContext.capture();
        Map<String, String> submittingMdc = MDC.getCopyOfContextMap();
        SecurityContext submittingSecurity = copySecurityContext(SecurityContextHolder.getContext());
        return () -> {
            UserContext.Snapshot previousUser = UserContext.capture();
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            SecurityContext previousSecurity = SecurityContextHolder.getContext();
            try {
                UserContext.restore(submittingUser);
                if (submittingMdc == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(submittingMdc);
                }
                SecurityContextHolder.setContext(submittingSecurity);
                task.run();
            } finally {
                UserContext.restore(previousUser);
                if (previousMdc == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previousMdc);
                }
                SecurityContextHolder.setContext(previousSecurity);
            }
        };
    }

    private SecurityContext copySecurityContext(SecurityContext source) {
        SecurityContext copy = SecurityContextHolder.createEmptyContext();
        copy.setAuthentication(source == null ? null : source.getAuthentication());
        return copy;
    }

    @Override
    public TaskExecutor getAsyncExecutor() {
        return taskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error("异步任务执行失败 method={} params={}",
                        method.getName(), Arrays.toString(params), ex);
                super.handleUncaughtException(ex, method, params);
            }
        };
    }
}
