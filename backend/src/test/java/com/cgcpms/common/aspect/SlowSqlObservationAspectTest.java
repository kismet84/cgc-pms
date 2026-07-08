package com.cgcpms.common.aspect;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SlowSqlObservationAspect - 慢 SQL 监控口径")
@ExtendWith(OutputCaptureExtension.class)
class SlowSqlObservationAspectTest {

    @Test
    @DisplayName("超过阈值的 mapper 调用应输出告警码并写入指标")
    void recordsSlowMapperCallWithLogAndMetric(CapturedOutput output) throws Throwable {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SlowSqlObservationAspect aspect = new SlowSqlObservationAspect(meterRegistry, 500L,
                clock(0L, 650_000_000L));
        ProceedingJoinPoint joinPoint = mapperJoinPoint("SysUserMapper.selectById(..)", "ok");

        Object result = aspect.observeMapperCall(joinPoint);

        assertEquals("ok", result);
        assertTrue(output.getOut().contains("SLOW_SQL_DETECTED"));
        assertTrue(output.getOut().contains("operation=SysUserMapper.selectById(..)"));
        assertTrue(output.getOut().contains("durationMs=650"));
        assertEquals(1.0, meterRegistry.find("db.sql.slow.count")
                .tag("operation", "SysUserMapper.selectById(..)")
                .counter()
                .count());
        assertEquals(1, meterRegistry.find("db.sql.duration")
                .tag("operation", "SysUserMapper.selectById(..)")
                .timer()
                .count());
    }

    @Test
    @DisplayName("低于阈值的 mapper 调用不应输出慢 SQL 告警")
    void skipsFastMapperCall(CapturedOutput output) throws Throwable {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SlowSqlObservationAspect aspect = new SlowSqlObservationAspect(meterRegistry, 500L,
                clock(0L, 120_000_000L));
        ProceedingJoinPoint joinPoint = mapperJoinPoint("SysUserMapper.selectById(..)", "ok");

        aspect.observeMapperCall(joinPoint);

        assertFalse(output.getOut().contains("SLOW_SQL_DETECTED"));
        assertNull(meterRegistry.find("db.sql.slow.count").counter());
        assertEquals(1, meterRegistry.find("db.sql.duration")
                .tag("operation", "SysUserMapper.selectById(..)")
                .timer()
                .count());
    }

    @Test
    @DisplayName("慢 SQL 日志不应泄露参数中的敏感值或连接串")
    void slowSqlLogDoesNotExposeSensitiveArguments(CapturedOutput output) throws Throwable {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SlowSqlObservationAspect aspect = new SlowSqlObservationAspect(meterRegistry, 1L,
                clock(0L, 2_000_000L));
        ProceedingJoinPoint joinPoint = mapperJoinPoint("PaymentMapper.selectSensitive(..)", "ok");
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                "jdbc:mysql://prod-db.internal:3306/cgc?password=secret",
                "token=plain-token",
                "amount=9999"
        });

        Object result = aspect.observeMapperCall(joinPoint);

        assertSame("ok", result);
        assertTrue(output.getOut().contains("SLOW_SQL_DETECTED"));
        assertFalse(output.getOut().contains("jdbc:mysql://"));
        assertFalse(output.getOut().contains("prod-db.internal"));
        assertFalse(output.getOut().contains("secret"));
        assertFalse(output.getOut().contains("plain-token"));
        assertFalse(output.getOut().contains("amount=9999"));
    }

    private ProceedingJoinPoint mapperJoinPoint(String operation, Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn(operation);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    private LongSupplier clock(long... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> values[Math.min(index.getAndIncrement(), values.length - 1)];
    }
}
