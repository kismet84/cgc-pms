package com.cgcpms.common.aspect;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@Slf4j
@Aspect
@Component
public class SlowSqlObservationAspect {

    private static final String UNKNOWN_OPERATION = "unknown";

    private final MeterRegistry meterRegistry;
    private final long thresholdMs;
    private final LongSupplier nanoTime;

    @Autowired
    public SlowSqlObservationAspect(MeterRegistry meterRegistry,
                                    @Value("${observability.slow-sql.threshold-ms:500}") long thresholdMs) {
        this(meterRegistry, thresholdMs, System::nanoTime);
    }

    SlowSqlObservationAspect(MeterRegistry meterRegistry, long thresholdMs, LongSupplier nanoTime) {
        this.meterRegistry = meterRegistry;
        this.thresholdMs = Math.max(1L, thresholdMs);
        this.nanoTime = nanoTime;
    }

    @Around("execution(* com.cgcpms..mapper..*(..))")
    public Object observeMapperCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = nanoTime.getAsLong();
        String operation = safeOperation(joinPoint);
        try {
            return joinPoint.proceed();
        } finally {
            long durationNanos = Math.max(0L, nanoTime.getAsLong() - start);
            long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
            recordDuration(operation, durationNanos);
            if (durationMs >= thresholdMs) {
                recordSlowCall(operation, durationMs);
            }
        }
    }

    private void recordDuration(String operation, long durationNanos) {
        Timer.builder("db.sql.duration")
                .description("Mapper invocation duration without SQL text or bind values")
                .tag("operation", operation)
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private void recordSlowCall(String operation, long durationMs) {
        Counter.builder("db.sql.slow.count")
                .description("Slow mapper invocation count")
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
        log.warn("SLOW_SQL_DETECTED operation={} durationMs={} thresholdMs={}",
                operation, durationMs, thresholdMs);
    }

    private String safeOperation(ProceedingJoinPoint joinPoint) {
        if (joinPoint == null || joinPoint.getSignature() == null) {
            return UNKNOWN_OPERATION;
        }
        String operation = joinPoint.getSignature().toShortString();
        if (operation == null || operation.isBlank()) {
            return UNKNOWN_OPERATION;
        }
        return operation;
    }
}
