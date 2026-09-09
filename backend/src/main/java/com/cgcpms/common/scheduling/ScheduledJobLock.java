package com.cgcpms.common.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cross-instance mutual exclusion for scheduled jobs, backed by {@code sys_scheduled_lock}.
 *
 * <p>Each job takes a lease rather than a lock held for the duration of a connection, so an
 * instance that dies mid-run does not block the job forever: the next trigger after
 * {@code lease_until} reclaims it. Pick a lease longer than the worst-case run time but shorter
 * than the trigger interval, otherwise a slow run and the next trigger can overlap.
 *
 * <p>The table carries no {@code tenant_id} and is reached only through {@link JdbcTemplate},
 * which keeps it outside the MyBatis-Plus tenant interceptor.
 */
@Slf4j
@Component
public class ScheduledJobLock {

    private final JdbcTemplate jdbcTemplate;
    private final String owner;

    public ScheduledJobLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.owner = resolveOwner();
    }

    /**
     * Run {@code task} only if this instance wins the lease.
     *
     * @return true when the task ran here, false when another instance holds the lease
     */
    public boolean runExclusively(String jobName, Duration lease, Runnable task) {
        if (!tryAcquire(jobName, lease)) {
            log.info("Scheduled job {} is held by another instance, skipping this trigger", jobName);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            release(jobName);
        }
    }

    boolean tryAcquire(String jobName, Duration lease) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plus(lease);
        try {
            jdbcTemplate.update(
                    "INSERT INTO sys_scheduled_lock (lock_name, locked_by, locked_at, lease_until) VALUES (?, ?, ?, ?)",
                    jobName, owner, now, leaseUntil);
            return true;
        } catch (DataIntegrityViolationException alreadyRegistered) {
            // Row exists: take it over only when the previous lease has expired.
            return jdbcTemplate.update(
                    "UPDATE sys_scheduled_lock SET locked_by = ?, locked_at = ?, lease_until = ? "
                            + "WHERE lock_name = ? AND lease_until <= ?",
                    owner, now, leaseUntil, jobName, now) == 1;
        }
    }

    void release(String jobName) {
        try {
            // Expire our own lease only; a lease already taken over by another instance stays put.
            jdbcTemplate.update(
                    "UPDATE sys_scheduled_lock SET lease_until = ? WHERE lock_name = ? AND locked_by = ?",
                    LocalDateTime.now(), jobName, owner);
        } catch (RuntimeException e) {
            log.warn("Failed to release scheduled job lock {}; it will expire with its lease", jobName, e);
        }
    }

    String owner() {
        return owner;
    }

    private static String resolveOwner() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown-host";
        }
        return host + "/" + UUID.randomUUID();
    }
}
