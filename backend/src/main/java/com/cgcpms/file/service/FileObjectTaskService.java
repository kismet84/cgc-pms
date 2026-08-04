package com.cgcpms.file.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
public class FileObjectTaskService {

    private static final int MAX_ATTEMPTS = 10;
    private static final int BATCH_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;
    private final ObjectProvider<FileObjectTaskService> selfProvider;

    @Transactional(propagation = Propagation.MANDATORY)
    public long enqueueDelete(long tenantId, String bucket, String path) {
        return insertDeleteTask(tenantId, bucket, path);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long enqueueDeleteRequiresNew(long tenantId, String bucket, String path) {
        return insertDeleteTask(tenantId, bucket, path);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processNow(long taskId) {
        Integer claimed = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_file_object_task
                WHERE id=? AND status IN ('PENDING','RETRY')
                """, Integer.class, taskId);
        if (claimed == null || claimed == 0) return;
        if (jdbcTemplate.update("""
                UPDATE sys_file_object_task
                SET status='PROCESSING',updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND status IN ('PENDING','RETRY')
                """, taskId) != 1) return;

        Map<String, Object> task = jdbcTemplate.queryForMap("""
                SELECT operation,source_bucket,source_path,attempt_count
                FROM sys_file_object_task WHERE id=?
                """, taskId);
        try {
            if (!"DELETE".equals(task.get("operation"))) {
                throw new IllegalStateException("Unsupported object task operation");
            }
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(String.valueOf(task.get("source_bucket")))
                    .object(String.valueOf(task.get("source_path")))
                    .build());
            jdbcTemplate.update("""
                    UPDATE sys_file_object_task
                    SET status='SUCCEEDED',completed_at=CURRENT_TIMESTAMP,last_error_code=NULL,
                        updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND status='PROCESSING'
                    """, taskId);
        } catch (Exception exception) {
            int attempts = ((Number) task.get("attempt_count")).intValue() + 1;
            String status = attempts >= MAX_ATTEMPTS ? "FAILED" : "RETRY";
            LocalDateTime retryAt = LocalDateTime.now().plusSeconds(Math.min(3600L, 1L << Math.min(attempts, 10)));
            jdbcTemplate.update("""
                    UPDATE sys_file_object_task
                    SET status=?,attempt_count=?,next_retry_at=?,last_error_code=?,updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND status='PROCESSING'
                    """, status, attempts, retryAt, exception.getClass().getSimpleName(), taskId);
            log.warn("File object task failed: taskId={}, attempt={}, errorType={}",
                    taskId, attempts, exception.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${file.object-task.retry-delay-ms:60000}") // SQL-SAFETY: fixed-sql-fragment — Spring configuration placeholder, not SQL
    public void retryPending() {
        jdbcTemplate.update("""
                UPDATE sys_file_object_task
                SET status='RETRY',next_retry_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE status='PROCESSING' AND updated_at<?
                """, LocalDateTime.now().minusMinutes(15));
        List<Long> taskIds = jdbcTemplate.queryForList("""
                SELECT id FROM sys_file_object_task
                WHERE status IN ('PENDING','RETRY') AND next_retry_at<=?
                ORDER BY next_retry_at,id LIMIT ?
                """, Long.class, LocalDateTime.now(), BATCH_SIZE);
        taskIds.forEach(taskId -> selfProvider.getObject().processNow(taskId));
    }

    private long insertDeleteTask(long tenantId, String bucket, String path) {
        String key = "DELETE:" + bucket + ":" + path;
        List<Long> existing = jdbcTemplate.queryForList("""
                SELECT id FROM sys_file_object_task WHERE tenant_id=? AND idempotency_key=?
                """, Long.class, tenantId, key);
        if (!existing.isEmpty()) return existing.getFirst();

        long id = IdWorker.getId();
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_file_object_task(
                        id,tenant_id,operation,source_bucket,source_path,idempotency_key,
                        status,attempt_count,next_retry_at,created_at,updated_at)
                    VALUES(?,?,'DELETE',?,?,?,'PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, id, tenantId, bucket, path, key);
            return id;
        } catch (DuplicateKeyException duplicate) {
            return jdbcTemplate.queryForObject("""
                    SELECT id FROM sys_file_object_task WHERE tenant_id=? AND idempotency_key=?
                    """, Long.class, tenantId, key);
        }
    }
}
