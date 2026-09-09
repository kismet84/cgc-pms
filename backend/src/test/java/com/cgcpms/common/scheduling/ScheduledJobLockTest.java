package com.cgcpms.common.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ScheduledJobLock — 定时任务跨实例互斥")
class ScheduledJobLockTest {

    private JdbcTemplate newDatabase(String name) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE sys_scheduled_lock (
                    lock_name   VARCHAR(100) NOT NULL,
                    locked_by   VARCHAR(200) NOT NULL,
                    locked_at   TIMESTAMP    NOT NULL,
                    lease_until TIMESTAMP    NOT NULL,
                    PRIMARY KEY (lock_name)
                )
                """);
        return jdbcTemplate;
    }

    @Test
    @DisplayName("第二个实例在租约有效期内拿不到锁，任务只跑一次")
    void secondInstanceSkipsWhileLeaseIsHeld() {
        JdbcTemplate jdbcTemplate = newDatabase("scheduled_lock_hold");
        ScheduledJobLock first = new ScheduledJobLock(jdbcTemplate);
        ScheduledJobLock second = new ScheduledJobLock(jdbcTemplate);
        AtomicInteger runs = new AtomicInteger();

        assertTrue(first.tryAcquire("job", Duration.ofMinutes(30)));
        assertFalse(second.runExclusively("job", Duration.ofMinutes(30), runs::incrementAndGet));

        assertEquals(0, runs.get());
        assertEquals(first.owner(), jdbcTemplate.queryForObject(
                "SELECT locked_by FROM sys_scheduled_lock WHERE lock_name='job'", String.class));
    }

    @Test
    @DisplayName("租约到期后其他实例可以抢占，不会因为实例崩溃永久卡住")
    void expiredLeaseIsReclaimed() {
        JdbcTemplate jdbcTemplate = newDatabase("scheduled_lock_expiry");
        ScheduledJobLock crashed = new ScheduledJobLock(jdbcTemplate);
        ScheduledJobLock next = new ScheduledJobLock(jdbcTemplate);

        // 崩溃的实例持有一个已经过期的租约，且从未 release
        assertTrue(crashed.tryAcquire("job", Duration.ofMinutes(-1)));

        AtomicInteger runs = new AtomicInteger();
        assertTrue(next.runExclusively("job", Duration.ofMinutes(30), runs::incrementAndGet));
        assertEquals(1, runs.get());
    }

    @Test
    @DisplayName("release 之后同一任务可以立即再次获取")
    void releaseAllowsImmediateReacquire() {
        JdbcTemplate jdbcTemplate = newDatabase("scheduled_lock_release");
        ScheduledJobLock owner = new ScheduledJobLock(jdbcTemplate);
        ScheduledJobLock other = new ScheduledJobLock(jdbcTemplate);

        assertTrue(owner.runExclusively("job", Duration.ofMinutes(30), () -> { }));
        assertTrue(other.tryAcquire("job", Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("任务抛异常也要释放租约")
    void leaseIsReleasedWhenTaskThrows() {
        JdbcTemplate jdbcTemplate = newDatabase("scheduled_lock_throw");
        ScheduledJobLock owner = new ScheduledJobLock(jdbcTemplate);
        ScheduledJobLock other = new ScheduledJobLock(jdbcTemplate);

        try {
            owner.runExclusively("job", Duration.ofMinutes(30), () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // 任务异常向上传播，租约仍需释放
        }

        assertTrue(other.tryAcquire("job", Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("release 不会顶掉已经被别人抢占的租约")
    void releaseDoesNotTouchAnotherOwnersLease() {
        JdbcTemplate jdbcTemplate = newDatabase("scheduled_lock_foreign");
        ScheduledJobLock stale = new ScheduledJobLock(jdbcTemplate);
        ScheduledJobLock active = new ScheduledJobLock(jdbcTemplate);

        assertTrue(stale.tryAcquire("job", Duration.ofMinutes(-1)));
        assertTrue(active.tryAcquire("job", Duration.ofMinutes(30)));

        stale.release("job");

        assertEquals(active.owner(), jdbcTemplate.queryForObject(
                "SELECT locked_by FROM sys_scheduled_lock WHERE lock_name='job'", String.class));
        assertFalse(stale.tryAcquire("job", Duration.ofMinutes(30)));
    }
}
