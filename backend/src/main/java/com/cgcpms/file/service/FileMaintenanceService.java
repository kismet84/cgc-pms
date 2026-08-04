package com.cgcpms.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.config.MinioConfig;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.file.scan.VirusScanner;
import com.cgcpms.file.vo.FileVirusScanStatus;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.messages.Item;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
public class FileMaintenanceService {

    private static final int MAX_RESCAN_BATCH = 200;
    private static final int MAX_SAMPLE = 100;
    private static final int MAX_FILE_SIZE = 20 * 1024 * 1024;

    private final SysFileMapper fileMapper;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final VirusScanner virusScanner;
    private final JdbcTemplate jdbcTemplate;

    public ReconciliationReport reconcile() {
        Long tenantId = requireTenantId();
        List<SysFile> files = fileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, tenantId)
                .orderByAsc(SysFile::getId));
        Set<String> activeTenantPaths = new HashSet<>();
        List<String> missing = new java.util.ArrayList<>();
        long missingCount = 0;
        long legacyMetadata = 0;
        String tenantPrefix = "tenants/" + tenantId + "/";

        for (SysFile file : files) {
            if (minioConfig.getBucket().equals(file.getBucketName())
                    && file.getStoragePath().startsWith(tenantPrefix)) {
                activeTenantPaths.add(file.getStoragePath());
            } else {
                legacyMetadata++;
            }
            try {
                minioClient.statObject(StatObjectArgs.builder()
                        .bucket(file.getBucketName()).object(file.getStoragePath()).build());
            } catch (Exception exception) {
                if (!isMissing(exception)) throw storageUnavailable();
                missingCount++;
                if (missing.size() < MAX_SAMPLE) missing.add(file.getId().toString());
            }
        }

        long objectCount = 0;
        long orphanObjectCount = 0;
        List<String> orphanObjects = new java.util.ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(minioConfig.getBucket()).prefix(tenantPrefix).recursive(true).build());
            for (Result<Item> result : results) {
                String path = result.get().objectName();
                objectCount++;
                if (!activeTenantPaths.contains(path)) {
                    orphanObjectCount++;
                    if (orphanObjects.size() < MAX_SAMPLE) orphanObjects.add(path);
                }
            }
        } catch (Exception exception) {
            throw storageUnavailable();
        }

        return new ReconciliationReport(
                tenantId, files.size(), objectCount, missingCount, List.copyOf(missing),
                orphanObjectCount, List.copyOf(orphanObjects), legacyMetadata,
                scalar("SELECT COUNT(*) FROM sys_file_object_task WHERE tenant_id=? AND status IN ('PENDING','PROCESSING','RETRY','FAILED')", tenantId),
                scalar("""
                        SELECT COUNT(*) FROM (
                          SELECT active_content_sha256 FROM sys_file
                          WHERE tenant_id=? AND deleted_flag=0 AND active_content_sha256 IS NOT NULL
                          GROUP BY business_type,business_id,active_content_sha256 HAVING COUNT(*)>1
                        ) duplicate_groups
                        """, tenantId),
                scalar("""
                        SELECT COUNT(*) FROM (
                          SELECT bucket_name,storage_path FROM sys_file
                          WHERE deleted_flag=0 GROUP BY bucket_name,storage_path
                          HAVING COUNT(DISTINCT tenant_id)>1
                        ) path_conflicts
                        """),
                relationOrphans(tenantId));
    }

    @Transactional(rollbackFor = Exception.class)
    public RescanReport rescan(long afterId, int requestedBatchSize) {
        Long tenantId = requireTenantId();
        int batchSize = Math.max(1, Math.min(MAX_RESCAN_BATCH, requestedBatchSize));
        List<SysFile> files = fileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, tenantId)
                .gt(SysFile::getId, Math.max(0, afterId))
                .orderByAsc(SysFile::getId)
                .last("LIMIT " + batchSize));
        int clean = 0;
        int infected = 0;
        int failed = 0;
        long nextAfterId = afterId;
        for (SysFile file : files) {
            nextAfterId = file.getId();
            VirusScanner.ScanResult result;
            try (var object = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(file.getBucketName()).object(file.getStoragePath()).build())) {
                byte[] content = object.readNBytes(MAX_FILE_SIZE + 1);
                result = content.length > MAX_FILE_SIZE
                        ? VirusScanner.ScanResult.unavailable("FILE_TOO_LARGE")
                        : virusScanner.scan(content);
            } catch (Exception exception) {
                result = VirusScanner.ScanResult.unavailable(isMissing(exception)
                        ? "FILE_OBJECT_MISSING" : exception.getClass().getSimpleName());
            }
            String status = switch (result.status()) {
                case CLEAN -> FileVirusScanStatus.CLEAN.name();
                case INFECTED -> FileVirusScanStatus.INFECTED.name();
                case UNAVAILABLE -> FileVirusScanStatus.FAILED.name();
            };
            clean += result.status() == VirusScanner.ScanResult.Status.CLEAN ? 1 : 0;
            infected += result.status() == VirusScanner.ScanResult.Status.INFECTED ? 1 : 0;
            failed += result.status() == VirusScanner.ScanResult.Status.UNAVAILABLE ? 1 : 0;
            jdbcTemplate.update("""
                    UPDATE sys_file SET virus_scan_status=?,virus_scan_detail=?,virus_scanned_at=?,updated_at=?
                    WHERE id=? AND tenant_id=? AND deleted_flag=0
                    """, status, result.detail(), LocalDateTime.now(), LocalDateTime.now(), file.getId(), tenantId);
        }
        return new RescanReport(tenantId, afterId, nextAfterId, files.size(), clean, infected, failed,
                files.size() == batchSize);
    }

    private long relationOrphans(Long tenantId) {
        return scalar("""
                SELECT COUNT(*) FROM bid_document_version v
                LEFT JOIN sys_file f ON f.id=v.sys_file_id AND f.tenant_id=v.tenant_id AND f.deleted_flag=0
                WHERE v.tenant_id=? AND v.deleted_flag=0 AND f.id IS NULL
                """, tenantId)
                + scalar("""
                SELECT COUNT(*) FROM payment_document_link l
                LEFT JOIN sys_file f ON f.id=l.file_id AND f.tenant_id=l.tenant_id AND f.deleted_flag=0
                WHERE l.tenant_id=? AND f.id IS NULL
                """, tenantId);
    }

    private long scalar(String sql, Object... args) {
        return Objects.requireNonNullElse(jdbcTemplate.queryForObject(sql, Long.class, args), 0L);
    }

    private boolean isMissing(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ErrorResponseException response) {
                String code = response.errorResponse().code();
                return "NoSuchKey".equals(code) || "NoSuchObject".equals(code);
            }
            current = current.getCause();
        }
        return false;
    }

    private BusinessException storageUnavailable() {
        return new BusinessException("FILE_STORAGE_UNAVAILABLE", "文件存储服务暂不可用，请稍后重试");
    }

    private Long requireTenantId() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少租户上下文");
        return tenantId;
    }

    public record ReconciliationReport(
            Long tenantId,
            long activeMetadata,
            long tenantObjectCount,
            long missingObjectCount,
            List<String> missingFileIdSample,
            long orphanObjectCount,
            List<String> orphanObjectPathSample,
            long legacyMetadataCount,
            long pendingCleanupTaskCount,
            long duplicateActiveGroupCount,
            long crossTenantPathConflictCount,
            long relationOrphanCount) {
    }

    public record RescanReport(
            Long tenantId,
            long afterId,
            long nextAfterId,
            int processed,
            int clean,
            int infected,
            int failed,
            boolean hasMore) {
    }
}
