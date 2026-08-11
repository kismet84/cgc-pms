package com.cgcpms.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.config.MinioConfig;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.file.scan.VirusScanner;
import com.cgcpms.file.vo.FileVirusScanStatus;
import com.cgcpms.file.vo.SysFileVO;
import com.cgcpms.security.BusinessAmountAccess;
import com.cgcpms.projectfile.ProjectFileService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ContentDisposition;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.cgcpms.common.util.DateTimeUtils;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
public class FileService {

    private static final int PRESIGNED_URL_EXPIRE_MINUTES = 5;
    private static final int MAX_GENERATED_PDF_BYTES = 20 * 1024 * 1024;
    private static final int MAX_PREVIEW_PDF_BYTES = 100 * 1024 * 1024;
    private static final Set<String> BID_DOCUMENT_TYPES = Set.of(
            "TENDER_DOCUMENT", "BILL_OF_QUANTITIES", "TENDER_DRAWING",
            "BID_PRICE", "TECHNICAL_DOCUMENT", "BID_DRAWING",
            "CANDIDATE_NOTICE", "AWARD_NOTICE", "LOSS_NOTICE",
            "OBJECTION_REPLY", "AWARD_CLARIFICATION", "OTHER_RESULT");

    private final SysFileMapper sysFileMapper;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final com.cgcpms.file.auth.BusinessObjectAuthorizer authorizer;
    private final RetryTemplate minioRetryTemplate;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final VirusScanner virusScanner;
    private final FileObjectTaskService objectTaskService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<ProjectFileService> projectFileServiceProvider;
    private final FileTypeValidator fileTypeValidator = new FileTypeValidator();

    @Autowired
    public FileService(SysFileMapper sysFileMapper, MinioClient minioClient, MinioConfig minioConfig,
                       com.cgcpms.file.auth.BusinessObjectAuthorizer authorizer,
                       RetryTemplate minioRetryTemplate, ObjectProvider<MeterRegistry> meterRegistryProvider,
                       VirusScanner virusScanner, FileObjectTaskService objectTaskService,
                       JdbcTemplate jdbcTemplate, ObjectProvider<ProjectFileService> projectFileServiceProvider) {
        this.sysFileMapper = sysFileMapper;
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
        this.authorizer = authorizer;
        this.minioRetryTemplate = minioRetryTemplate;
        this.meterRegistryProvider = meterRegistryProvider;
        this.virusScanner = virusScanner;
        this.objectTaskService = objectTaskService;
        this.jdbcTemplate = jdbcTemplate;
        this.projectFileServiceProvider = projectFileServiceProvider;
    }

    /** Compatibility constructor used by focused legacy unit tests. */
    public FileService(SysFileMapper sysFileMapper, MinioClient minioClient, MinioConfig minioConfig,
                       com.cgcpms.file.auth.BusinessObjectAuthorizer authorizer,
                       RetryTemplate minioRetryTemplate, ObjectProvider<MeterRegistry> meterRegistryProvider,
                       VirusScanner virusScanner, FileObjectTaskService objectTaskService,
                       JdbcTemplate jdbcTemplate) {
        this(sysFileMapper, minioClient, minioConfig, authorizer, minioRetryTemplate,
                meterRegistryProvider, virusScanner, objectTaskService, jdbcTemplate, null);
    }

    /** Compatibility constructor used by focused legacy unit tests. */
    public FileService(SysFileMapper sysFileMapper, MinioClient minioClient, MinioConfig minioConfig,
                       com.cgcpms.file.auth.BusinessObjectAuthorizer authorizer,
                       RetryTemplate minioRetryTemplate, ObjectProvider<MeterRegistry> meterRegistryProvider,
                       VirusScanner virusScanner, FileObjectTaskService objectTaskService) {
        this(sysFileMapper, minioClient, minioConfig, authorizer, minioRetryTemplate,
                meterRegistryProvider, virusScanner, objectTaskService, null, null);
    }

    /**
     * Upload a file and associate it with a business entity.
     */
    @Transactional(rollbackFor = Exception.class)
    @CircuitBreaker(name = "minio", fallbackMethod = "uploadFallback")
    public SysFileVO upload(MultipartFile file, String businessType, Long businessId) {
        return upload(file, businessType, businessId, "OTHER");
    }

    @Transactional(rollbackFor = Exception.class)
    @CircuitBreaker(name = "minio", fallbackMethod = "uploadFallback")
    public SysFileVO upload(MultipartFile file, String businessType, Long businessId, String documentType) {
        try {
            return doUpload(file, businessType, businessId, documentType);
        } catch (BusinessException e) {
            recordUploadFailure(e.getCode());
            throw e;
        }
    }

    private SysFileVO doUpload(MultipartFile file, String businessType, Long businessId, String documentType) {
        if (file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "上传文件不能为空");
        }

        String normalizedBusinessType = normalizeBusinessType(businessType, businessId);
        String normalizedDocumentType = normalizeDocumentType(documentType, normalizedBusinessType);
        authorizer.checkUploadAccess(normalizedBusinessType, businessId, normalizedDocumentType);
        authorizer.checkVariationDocumentStage(normalizedBusinessType, businessId, normalizedDocumentType);

        // 权限通过后再读取和扫描不可信内容。
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException("FILE_EMPTY", "无法读取文件内容");
        }
        FileTypeValidator.ValidationResult vr = fileTypeValidator.validate(
                file.getOriginalFilename(), file.getContentType(), content);

        scanOrReject(content);

        try {
            requireFileTransaction();
            Long tenantId = requireTenantId();
            Long fileId = IdWorker.getId();
            String originalName = vr.sanitizedName();
            String contentSha256 = sha256Hex(content);
            String fileName = contentSha256 + vr.extension();
            String storagePath = buildStoragePath(tenantId, normalizedBusinessType, businessId, fileId, fileName);
            String bucketName = minioConfig.getBucket();
            String contentType = vr.detectedMime();

            rejectDuplicateFile(normalizedBusinessType, businessId, contentSha256);
            putObjectWithRetry(bucketName, storagePath, contentType, content);
            registerRollbackObjectCleanup(tenantId, bucketName, storagePath);

            // Persist file record
            SysFile sysFile = new SysFile();
            sysFile.setId(fileId);
            sysFile.setTenantId(tenantId);
            sysFile.setBusinessType(normalizedBusinessType);
            sysFile.setDocumentType(normalizedDocumentType);
            sysFile.setBusinessId(businessId);
            sysFile.setFileName(fileName);
            sysFile.setOriginalName(originalName);
            sysFile.setFileSize((long) content.length);
            sysFile.setContentType(contentType);
            sysFile.setStoragePath(storagePath);
            sysFile.setBucketName(bucketName);
            sysFile.setVirusScanStatus(FileVirusScanStatus.CLEAN.name());
            sysFile.setVirusScanDetail(null);
            sysFile.setVirusScannedAt(LocalDateTime.now());
            sysFileMapper.insert(sysFile);
            if (projectFileServiceProvider != null) {
                projectFileServiceProvider.ifAvailable(service -> service.indexBusinessFile(sysFile));
            }

            return toVO(sysFile);

        } catch (DuplicateKeyException e) {
            throw new BusinessException("FILE_DUPLICATE", "文件已存在，请勿重复上传");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("File upload failed: businessType={}, businessId={}", businessType, businessId, e);
            if (isStorageUnavailable(e)) {
                throw new BusinessException("FILE_STORAGE_UNAVAILABLE", "文件服务暂不可用，请稍后重试");
            }
            throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败，请稍后重试");
        }
    }

    /**
     * Get a presigned download URL for an existing file.
     */
    public String getPresignedUrl(Long fileId) {
        return getPresignedFileUrl(fileId).url();
    }

    public PresignedFileUrl getPresignedFileUrl(Long fileId) {
        SysFile sysFile = sysFileMapper.selectById(fileId);
        if (sysFile == null) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        if (!sysFile.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        // 业务对象读权限校验
        authorizer.checkReadAccess(sysFile.getBusinessType(), sysFile.getBusinessId());
        ensureDownloadAllowed(sysFile);
        statObjectOrReject(sysFile);
        try {
            return new PresignedFileUrl(
                    genPresignedUrl(sysFile.getBucketName(), sysFile.getStoragePath(), sysFile),
                    sysFile.getBusinessType(), sysFile.getBusinessId(), sysFile.getId());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for file: {}", fileId, e);
            throw new BusinessException("FILE_URL_ERROR", "获取下载链接失败，请稍后重试");
        }
    }

    public Optional<FileAuditBinding> findAuditBinding(Long fileId) {
        SysFile file = sysFileMapper.selectById(fileId);
        if (file == null || !java.util.Objects.equals(file.getTenantId(), UserContext.getCurrentTenantId())) {
            return Optional.empty();
        }
        return Optional.of(new FileAuditBinding(file.getBusinessType(), file.getBusinessId()));
    }

    /** Download contract for immutable generated documents. */
    public String getGeneratedDocumentPresignedUrl(Long fileId) {
        SysFile sysFile = requireGeneratedDocument(fileId);
        authorizer.checkGeneratedDocumentAccess(sysFile.getBusinessType(), sysFile.getBusinessId());
        ensureDownloadAllowed(sysFile);
        statObjectOrReject(sysFile);
        return genPresignedUrl(sysFile.getBucketName(), sysFile.getStoragePath(), sysFile);
    }

    /** Audit-only contract; caller must enforce audit role and authority before bypassing current business visibility. */
    public String getGeneratedDocumentAuditPresignedUrl(Long fileId) {
        SysFile sysFile = requireGeneratedDocument(fileId);
        ensureDownloadAllowed(sysFile);
        statObjectOrReject(sysFile);
        return genPresignedUrl(sysFile.getBucketName(), sysFile.getStoragePath(), sysFile);
    }

    private SysFile requireGeneratedDocument(Long fileId) {
        SysFile sysFile = sysFileMapper.selectById(fileId);
        if (sysFile == null || !java.util.Objects.equals(sysFile.getTenantId(), UserContext.getCurrentTenantId())
                || !"GENERATED_DOCUMENT".equals(sysFile.getDocumentType())) {
            throw new BusinessException("FILE_NOT_FOUND", "生成文档不存在");
        }
        return sysFile;
    }

    /**
     * Archive a server-generated PDF without routing it through the untrusted multipart upload contract.
     * The caller must already have checked business read/generation authority.
     */
    @Transactional(rollbackFor = Exception.class)
    public GeneratedFileArchive archiveGeneratedPdf(byte[] content, String businessType, Long businessId,
                                                     String generationNo, String expectedSha256) {
        return archiveGeneratedPdf(content, businessType, businessId, generationNo, expectedSha256, null);
    }

    /** Archive PDF and expose the business number as the reader/download filename. */
    @Transactional(rollbackFor = Exception.class)
    public GeneratedFileArchive archiveGeneratedPdf(byte[] content, String businessType, Long businessId,
                                                     String generationNo, String expectedSha256,
                                                     String outputFileName) {
        String normalizedBusinessType = normalizeBusinessType(businessType, businessId);
        if (content == null || content.length < 5
                || !"%PDF-".equals(new String(content, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new BusinessException("DOCUMENT_OUTPUT_INVALID", "归档内容不是有效PDF");
        }
        if (content.length > MAX_GENERATED_PDF_BYTES) {
            throw new BusinessException("DOCUMENT_OUTPUT_TOO_LARGE", "归档PDF超过大小限制");
        }
        String actualSha256 = sha256Hex(content);
        if (expectedSha256 == null || !actualSha256.equalsIgnoreCase(expectedSha256)) {
            throw new BusinessException("DOCUMENT_OUTPUT_HASH_MISMATCH", "PDF归档哈希校验失败");
        }
        if (generationNo == null || !generationNo.matches("[A-Za-z0-9_-]{1,50}")) {
            throw new BusinessException("DOCUMENT_GENERATION_NO_INVALID", "文档生成编号格式非法");
        }

        Long tenantId = requireTenantId();
        Long fileId = IdWorker.getId();
        String bucketName = minioConfig.getBucket();
        String fileName = generationNo + "-" + actualSha256.substring(0, 16) + ".pdf";
        String storagePath = buildStoragePath(tenantId, normalizedBusinessType, businessId, fileId, fileName);
        try {
            requireFileTransaction();
            putObjectWithRetry(bucketName, storagePath, "application/pdf", content);
            registerRollbackObjectCleanup(tenantId, bucketName, storagePath);

            SysFile sysFile = new SysFile();
            sysFile.setId(fileId);
            sysFile.setTenantId(tenantId);
            sysFile.setBusinessType(normalizedBusinessType);
            sysFile.setDocumentType("GENERATED_DOCUMENT");
            sysFile.setBusinessId(businessId);
            sysFile.setFileName(fileName);
            String safeOutputName = outputFileName != null && outputFileName.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
                    ? outputFileName : generationNo;
            sysFile.setOriginalName(safeOutputName + ".pdf");
            sysFile.setFileSize((long) content.length);
            sysFile.setContentType("application/pdf");
            sysFile.setStoragePath(storagePath);
            sysFile.setBucketName(bucketName);
            sysFile.setVirusScanStatus(FileVirusScanStatus.CLEAN.name());
            sysFile.setVirusScanDetail("SERVER_GENERATED");
            sysFile.setVirusScannedAt(LocalDateTime.now());
            sysFileMapper.insert(sysFile);
            return new GeneratedFileArchive(sysFile.getId(), actualSha256, content.length);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Generated PDF archive failed: businessType={}, businessId={}, generationNo={}",
                    businessType, businessId, generationNo, exception);
            if (isStorageUnavailable(exception)) {
                throw new BusinessException("FILE_STORAGE_UNAVAILABLE", "文件服务暂不可用，请稍后重试");
            }
            throw new BusinessException("DOCUMENT_ARCHIVE_FAILED", "生成文档归档失败，请稍后重试", exception);
        }
    }

    /**
     * Delete a file (logical delete in DB + remove from MinIO).
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long fileId) {
        SysFile candidate = requireOwnedMutableFile(fileId);
        // 业务对象删除权限校验
        authorizer.checkDeleteAccess(candidate.getBusinessType(), candidate.getBusinessId(), candidate.getDocumentType());
        authorizer.checkVariationDocumentStage(candidate.getBusinessType(), candidate.getBusinessId(), candidate.getDocumentType());
        SysFile sysFile = lockOwnedMutableFileForDelete(candidate);
        ensureNoImmutableReferences(sysFile);
        deleteStoredFile(sysFile);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteForBusinessCascade(Long fileId, String businessType, Long businessId) {
        String normalizedBusinessType = normalizeBusinessType(businessType, businessId);
        SysFile candidate = requireOwnedMutableFile(fileId);
        if (!normalizedBusinessType.equalsIgnoreCase(candidate.getBusinessType()) || !businessId.equals(candidate.getBusinessId())) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        SysFile sysFile = lockOwnedMutableFileForDelete(candidate);
        ensureNoImmutableReferences(sysFile);
        deleteStoredFile(sysFile);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAllForBusinessCascade(String businessType, Long businessId) {
        String normalizedBusinessType = normalizeBusinessType(businessType, businessId);
        List<SysFile> files = sysFileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                .apply("UPPER(TRIM(business_type)) = {0}", normalizedBusinessType) // SQL-SAFETY: fixed-sql-fragment — value uses MyBatis parameter binding
                .eq(SysFile::getBusinessId, businessId)
                .eq(SysFile::getTenantId, requireTenantId()));
        for (SysFile file : files) {
            SysFile locked = lockOwnedMutableFileForDelete(file);
            ensureNoImmutableReferences(locked);
            deleteStoredFile(locked);
        }
    }

    private SysFile requireOwnedMutableFile(Long fileId) {
        SysFile sysFile = sysFileMapper.selectById(fileId);
        if (sysFile == null || !sysFile.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        if ("GENERATED_DOCUMENT".equals(sysFile.getDocumentType())) {
            throw new BusinessException("FILE_IMMUTABLE", "已归档生成文档不可删除");
        }
        return sysFile;
    }

    private SysFile lockOwnedMutableFileForDelete(SysFile candidate) {
        sysFileMapper.lockActiveByObjectPath(candidate.getBucketName(), candidate.getStoragePath());
        SysFile locked = sysFileMapper.selectByIdForUpdate(candidate.getId(), candidate.getTenantId());
        if (locked == null) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        if ("GENERATED_DOCUMENT".equals(locked.getDocumentType())) {
            throw new BusinessException("FILE_IMMUTABLE", "已归档生成文档不可删除");
        }
        return locked;
    }

    private void ensureNoImmutableReferences(SysFile file) {
        Integer projectFileTableCount = jdbcTemplate == null ? 0 : jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE LOWER(table_name)='project_file_version_link'
                    """, Integer.class);
        Long managedProjectFileReferences = projectFileTableCount == null || projectFileTableCount == 0 ? 0L
                : jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM project_file_version_link v
                    JOIN project_file_catalog c ON c.tenant_id=v.tenant_id AND c.id=v.catalog_id
                    WHERE v.tenant_id=? AND v.sys_file_id=? AND v.deleted_flag=0
                      AND c.deleted_flag=0 AND c.source_kind='MANAGED'
                    """, Long.class, file.getTenantId(), file.getId());
        if (sysFileMapper.countImmutableReferences(file.getTenantId(), file.getId()) > 0
                || (managedProjectFileReferences != null && managedProjectFileReferences > 0)) {
            throw new BusinessException("FILE_IMMUTABLE", "文件已被不可变业务事实引用");
        }
    }

    /** Internal conversion input. Caller must hold an authorized persisted preview task. */
    public InternalFileContent readCleanObjectForInternalConversion(long tenantId, long fileId) {
        SysFile file = sysFileMapper.selectById(fileId);
        if (file == null || !Objects.equals(file.getTenantId(), tenantId)) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        ensureDownloadAllowed(file);
        statObjectOrReject(file);
        try {
            byte[] content = minioRetryTemplate.execute(context -> {
                try (var input = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(file.getBucketName()).object(file.getStoragePath()).build())) {
                    return input.readAllBytes();
                }
            });
            if (content == null || content.length == 0 || content.length > 20 * 1024 * 1024) {
                throw new BusinessException("OFFICE_PREVIEW_SOURCE_INVALID", "Office预览源文件无效");
            }
            String identity = file.getFileName() != null
                    && file.getFileName().matches("^[0-9a-f]{64}\\..+$")
                    ? file.getFileName().substring(0, 64) : sha256Hex(content);
            return new InternalFileContent(content, file.getOriginalName(), identity);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("FILE_STORAGE_UNAVAILABLE", "文件存储服务暂不可用，请稍后重试", exception);
        }
    }

    /** Store a scanned PDF derivative without creating a second sys_file fact. */
    public String storeDerivedPreview(long tenantId, long fileId, String contentIdentity, byte[] pdf) {
        if (pdf == null || pdf.length < 5 || pdf.length > MAX_PREVIEW_PDF_BYTES
                || !"%PDF-".equals(new String(pdf, 0, 5, StandardCharsets.US_ASCII))) {
            throw new BusinessException("OFFICE_PREVIEW_OUTPUT_INVALID", "Office预览输出无效");
        }
        SysFile source = sysFileMapper.selectById(fileId);
        if (source == null || !Objects.equals(source.getTenantId(), tenantId)) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        if (contentIdentity == null || !contentIdentity.matches("[0-9a-f]{64}")) {
            throw new BusinessException("OFFICE_PREVIEW_SOURCE_INVALID", "Office预览内容标识无效");
        }
        scanOrReject(pdf);
        String outputSha256 = sha256Hex(pdf);
        String path = "tenants/" + tenantId + "/derived-preview/" + fileId + "/"
                + contentIdentity + "-" + outputSha256 + ".pdf";
        try {
            putObjectWithRetry(source.getBucketName(), path, "application/pdf", pdf);
            return path;
        } catch (Exception exception) {
            throw new BusinessException("FILE_STORAGE_UNAVAILABLE", "文件存储服务暂不可用，请稍后重试", exception);
        }
    }

    public String getDerivedPreviewPresignedUrl(long fileId, String path) {
        SysFile source = sysFileMapper.selectById(fileId);
        if (source == null || !Objects.equals(source.getTenantId(), UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        authorizer.checkReadAccess(source.getBusinessType(), source.getBusinessId());
        ensureDownloadAllowed(source);
        String prefix = "tenants/" + source.getTenantId() + "/derived-preview/" + source.getId() + "/";
        if (path == null || !path.startsWith(prefix) || !path.endsWith(".pdf")) {
            throw new BusinessException("PROJECT_FILE_PREVIEW_NOT_READY", "预览尚未就绪");
        }
        try {
            statObjectWithRetry(source.getBucketName(), path);
        } catch (Exception exception) {
            throw new BusinessException("FILE_STORAGE_UNAVAILABLE", "文件存储服务暂不可用，请稍后重试", exception);
        }
        SysFile preview = new SysFile();
        preview.setContentType("application/pdf");
        preview.setOriginalName(source.getOriginalName() + ".pdf");
        return genPresignedUrl(source.getBucketName(), path, preview);
    }

    public void deleteDerivedPreviewLater(long tenantId, String path) {
        objectTaskService.enqueueDeleteRequiresNew(tenantId, minioConfig.getBucket(), path);
    }

    private void deleteStoredFile(SysFile sysFile) {
        Long fileId = sysFile.getId();
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            log.error("File delete requires active transaction synchronization: fileId={}, storagePath={}",
                    fileId, sysFile.getStoragePath());
            throw new BusinessException("FILE_DELETE_FAILED", "文件删除失败，请稍后重试");
        }

        if (projectFileServiceProvider != null) {
            projectFileServiceProvider.ifAvailable(service -> service.invalidateBusinessFile(sysFile));
        }

        // Logical delete in DB
        if (sysFileMapper.deleteById(fileId) != 1) {
            throw new BusinessException("FILE_DELETE_FAILED", "文件删除失败，请稍后重试");
        }

        if (sysFileMapper.countActiveByObjectPath(sysFile.getBucketName(), sysFile.getStoragePath()) > 0) {
            return;
        }
        long taskId = objectTaskService.enqueueDelete(
                sysFile.getTenantId(), sysFile.getBucketName(), sysFile.getStoragePath());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                objectTaskService.processNow(taskId);
            }
        });
    }

    /**
     * List files associated with a business entity.
     */
    public List<SysFileVO> listByBusiness(String businessType, Long businessId) {
        String normalizedBusinessType = normalizeBusinessType(businessType, businessId);
        // 业务对象读权限校验
        authorizer.checkReadAccess(normalizedBusinessType, businessId);

        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("UPPER(TRIM(business_type)) = {0}", normalizedBusinessType) // SQL-SAFETY: fixed-sql-fragment — value uses MyBatis parameter binding
                .eq(SysFile::getBusinessId, businessId)
                .eq(SysFile::getTenantId, UserContext.getCurrentTenantId())
                .orderByDesc(SysFile::getCreatedAt);

        List<SysFile> files = sysFileMapper.selectList(wrapper);

        return files.stream().map(this::toVO).toList();
    }

    private SysFileVO uploadFallback(MultipartFile file, String businessType, Long businessId, Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            throw businessException;
        }
        log.error("MinIO circuit breaker fallback on upload: businessType={}, businessId={}", businessType, businessId, throwable);
        recordUploadFailure("FILE_STORAGE_UNAVAILABLE");
        throw new BusinessException("FILE_STORAGE_UNAVAILABLE", "文件服务暂不可用，请稍后重试");
    }

    @SuppressWarnings("unused")
    private SysFileVO uploadFallback(MultipartFile file, String businessType, Long businessId,
                                     String documentType, Throwable throwable) {
        return uploadFallback(file, businessType, businessId, throwable);
    }

    // ---- private helpers ----

    private void validateBusinessBindingParams(String businessType, Long businessId) {
        if (businessType == null || businessType.isBlank()) {
            throw new BusinessException("FILE_PARAM_MISSING", "业务类型不能为空");
        }
        if (businessId == null) {
            throw new BusinessException("FILE_PARAM_MISSING", "业务ID不能为空");
        }
        if (!businessType.matches("[A-Za-z0-9_-]+")) {
            throw new BusinessException("FILE_PARAM_INVALID", "业务类型格式非法");
        }
    }

    private String normalizeBusinessType(String businessType, Long businessId) {
        String normalized = businessType == null ? null : businessType.trim().toUpperCase();
        validateBusinessBindingParams(normalized, businessId);
        return normalized;
    }

    private Long requireTenantId() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少租户上下文");
        return tenantId;
    }

    private String buildStoragePath(Long tenantId, String businessType, Long businessId,
                                    Long fileId, String fileName) {
        return "tenants/" + tenantId + "/" + businessType + "/" + businessId
                + "/files/" + fileId + "/" + fileName;
    }

    private void scanOrReject(byte[] content) {
        VirusScanner.ScanResult result = virusScanner.scan(content);
        if (result.status() == VirusScanner.ScanResult.Status.CLEAN) {
            return;
        }
        if (result.status() == VirusScanner.ScanResult.Status.INFECTED) {
            log.warn("File upload rejected by virus scanner: threat={}", result.detail());
            throw new BusinessException("FILE_VIRUS_DETECTED", "文件安全检查未通过，已拒绝上传");
        }
        throw new BusinessException("FILE_VIRUS_SCAN_UNAVAILABLE", "文件安全检查服务暂不可用，请稍后重试");
    }

    private void ensureDownloadAllowed(SysFile file) {
        if (!BusinessAmountAccess.canView() && !isEmployeeEvidence(file)) {
            throw new BusinessException("AMOUNT_DOWNLOAD_FORBIDDEN", "当前账号无价格型下载权限");
        }
        if (!isScanClean(file)) {
            throw new BusinessException("FILE_VIRUS_SCAN_REQUIRED", "文件尚未通过安全检查，暂不可下载");
        }
    }

    private boolean isEmployeeEvidence(SysFile file) {
        if ("GENERATED_DOCUMENT".equals(file.getDocumentType())) return false;
        if ("SITE_DAILY_LOG".equals(file.getBusinessType())) return true;
        return "QS_RECTIFICATION".equals(file.getBusinessType())
                && ("RECTIFICATION_EVIDENCE".equals(file.getDocumentType())
                || "REINSPECTION_EVIDENCE".equals(file.getDocumentType()));
    }

    private boolean isScanClean(SysFile file) {
        return FileVirusScanStatus.CLEAN.name().equals(file.getVirusScanStatus());
    }

    private void putObjectWithRetry(String bucketName, String storagePath, String contentType, byte[] content)
            throws Exception {
        minioRetryTemplate.execute(context -> {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storagePath)
                    .stream(new java.io.ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
            return null;
        });
    }

    private void statObjectWithRetry(String bucketName, String storagePath) throws Exception {
        minioRetryTemplate.execute(context -> {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(storagePath).build());
            return null;
        });
    }

    private void statObjectOrReject(SysFile file) {
        try {
            statObjectWithRetry(file.getBucketName(), file.getStoragePath());
        } catch (Exception exception) {
            log.error("File object missing or unavailable: fileId={}, errorType={}",
                    file.getId(), exception.getClass().getSimpleName());
            if (isMissingObject(exception)) {
                throw new BusinessException("FILE_OBJECT_MISSING", "文件对象缺失，已进入一致性检查");
            }
            throw new BusinessException("FILE_STORAGE_UNAVAILABLE", "文件存储服务暂不可用，请稍后重试");
        }
    }

    private void registerRollbackObjectCleanup(Long tenantId, String bucketName, String storagePath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) return;
                try {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucketName).object(storagePath).build());
                } catch (Exception cleanupFailure) {
                    objectTaskService.enqueueDeleteRequiresNew(tenantId, bucketName, storagePath);
                    log.error("Rollback cleanup deferred: storagePath={}, errorType={}",
                            storagePath, cleanupFailure.getClass().getSimpleName());
                }
            }
        });
    }

    private void requireFileTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new BusinessException("FILE_TRANSACTION_REQUIRED", "文件写入需要事务上下文");
        }
    }

    private void rejectDuplicateFile(String businessType, Long businessId, String contentSha256) {
        Long duplicates = sysFileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, UserContext.getCurrentTenantId())
                .eq(SysFile::getBusinessType, businessType)
                .eq(SysFile::getBusinessId, businessId)
                .apply("SUBSTRING(file_name, 1, 64) = {0}", contentSha256)); // SQL-SAFETY: fixed-sql-fragment — hash uses MyBatis parameter binding
        if (duplicates != null && duplicates > 0) {
            throw new BusinessException("FILE_DUPLICATE", "文件已存在，请勿重复上传");
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception e) {
            throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败，请稍后重试");
        }
    }

    private boolean isStorageUnavailable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof SocketTimeoutException
                    || current instanceof UnknownHostException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isMissingObject(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof io.minio.errors.ErrorResponseException response) {
                String code = response.errorResponse().code();
                return "NoSuchKey".equals(code) || "NoSuchObject".equals(code);
            }
            current = current.getCause();
        }
        return false;
    }

    private void recordUploadFailure(String code) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        Counter.builder("file.upload.failures")
                .tag("code", metricTag(code))
                .register(registry)
                .increment();
    }

    private String metricTag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private String genPresignedUrl(String bucket, String object) {
        return genPresignedUrl(bucket, object, null);
    }

    private String genPresignedUrl(String bucket, String object, SysFile file) {
        try {
            Map<String, String> extraQueryParams = new HashMap<>();
            if (file != null) {
                boolean inline = "application/pdf".equalsIgnoreCase(file.getContentType())
                        || (file.getContentType() != null && file.getContentType().toLowerCase().startsWith("image/"));
                String disposition = ContentDisposition.builder(inline ? "inline" : "attachment")
                        .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                        .build().toString();
                extraQueryParams.put("response-content-type", isTextFile(file)
                        ? "text/plain; charset=utf-8"
                        : java.util.Objects.requireNonNullElse(file.getContentType(), "application/octet-stream"));
                extraQueryParams.put("response-content-disposition", disposition);
            }
            String url = presignClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(object)
                            .region(minioConfig.getRegion())
                            .extraQueryParams(extraQueryParams)
                            .expiry(PRESIGNED_URL_EXPIRE_MINUTES, TimeUnit.MINUTES)
                            .method(Method.GET)
                            .build());
            if (!isPresignedUrl(url)) {
                throw new BusinessException("FILE_URL_ERROR", "生成下载链接失败");
            }
            return url;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: bucket={}, object={}", bucket, object, e);
            throw new BusinessException("FILE_URL_ERROR", "生成下载链接失败: " + e.getMessage());
        }
    }

    private MinioClient presignClient() {
        return minioConfig.hasPublicEndpoint() ? minioConfig.presignClient() : minioClient;
    }

    private boolean isPresignedUrl(String url) {
        return url != null
                && url.contains("X-Amz-Signature=")
                && url.contains("X-Amz-Expires=" + TimeUnit.MINUTES.toSeconds(PRESIGNED_URL_EXPIRE_MINUTES));
    }

    private boolean isTextFile(SysFile file) {
        String contentType = file.getContentType();
        String fileName = file.getFileName();
        return "text/plain".equalsIgnoreCase(contentType)
                || (fileName != null && (fileName.endsWith(".txt") || fileName.endsWith(".csv")));
    }

    private SysFileVO toVO(SysFile f) {
        SysFileVO vo = new SysFileVO();
        vo.setId(f.getId() == null ? null : String.valueOf(f.getId()));
        vo.setBusinessType(f.getBusinessType() == null ? null : f.getBusinessType().trim().toUpperCase());
        vo.setDocumentType(f.getDocumentType());
        vo.setBusinessId(f.getBusinessId() == null ? null : String.valueOf(f.getBusinessId()));
        vo.setOriginalName(f.getOriginalName());
        vo.setFileSize(f.getFileSize());
        vo.setContentType(f.getContentType());
        applyVirusScanStatus(f, vo);
        if (f.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(f.getCreatedAt()));
        return vo;
    }

    public record PresignedFileUrl(String url, String businessType, Long businessId, Long fileId) {}

    public record FileAuditBinding(String businessType, Long businessId) {}

    public record InternalFileContent(byte[] content, String originalName, String contentIdentity) {}

    private String normalizeDocumentType(String documentType, String businessType) {
        String type = documentType == null ? "OTHER" : documentType.trim().toUpperCase();
        String business = businessType == null ? "" : businessType.trim().toUpperCase();
        if ("OTHER".equals(type)) {
            type = switch (business) {
                case "INVOICE" -> "ELECTRONIC_INVOICE";
                case "SALES_INVOICE" -> "ELECTRONIC_INVOICE";
                case "COLLECTION_RECORD" -> "BANK_RECEIPT";
                case "OWNER_SETTLEMENT", "CONTRACT_REVENUE" -> "CONTRACT_ATTACHMENT";
                case "PAYMENT" -> "PAYMENT_PROOF";
                case "CASH_JOURNAL" -> "BANK_RECEIPT";
                case "CONTRACT" -> "CONTRACT_ATTACHMENT";
                case "PROJECT_COMMENCEMENT" -> "COMMENCEMENT_BASIS";
                case "VARIATION" -> "SITE_EVIDENCE";
                case "QS_INSPECTION" -> "INSPECTION_EVIDENCE";
                case "QS_ISSUE" -> "ISSUE_EVIDENCE";
                case "QS_RECTIFICATION" -> "RECTIFICATION_EVIDENCE";
                case "SUPPLIER_SOURCING" -> "SOURCING_REQUIREMENT";
                case "SUPPLIER_QUOTE" -> "QUOTE_ATTACHMENT";
                case "TECH_SCHEME" -> "SCHEME_FILE";
                case "TECH_DRAWING_VERSION" -> "DRAWING_FILE";
                case "TECH_DRAWING_REVIEW" -> "REVIEW_MINUTES";
                case "TECH_RFI" -> "RFI_EVIDENCE";
                case "TECH_RFI_RESPONSE" -> "DESIGN_RESPONSE";
                case "TECH_DISCLOSURE" -> "DISCLOSURE_RECORD";
                case "TECH_ARCHIVE" -> "ACCEPTANCE_ARCHIVE";
                case "CLOSEOUT_SECTION_ACCEPTANCE" -> "SECTION_ACCEPTANCE_RECORD";
                case "CLOSEOUT_FINAL_ACCEPTANCE" -> "FINAL_ACCEPTANCE_CERTIFICATE";
                case "CLOSEOUT_DEFECT" -> "DEFECT_RECTIFICATION_EVIDENCE";
                case "CLOSEOUT_WARRANTY" -> "WARRANTY_RELEASE_VOUCHER";
                case "CLOSEOUT_ARCHIVE_TRANSFER" -> "ARCHIVE_TRANSFER_LIST";
                default -> "OTHER";
            };
        }
        boolean productionMeasurementEvidence = "PRODUCTION_MEASUREMENT".equals(business)
                && ("MEASUREMENT_GENERAL".equals(type) || type.matches("ML_\\d+"));
        boolean bidDocument = "BID_COST".equals(business) && BID_DOCUMENT_TYPES.contains(type);
        if (!productionMeasurementEvidence && !bidDocument && !Set.of("ELECTRONIC_INVOICE", "SCANNED_INVOICE", "BANK_RECEIPT",
                "CONTRACT_ATTACHMENT", "PAYMENT_PROOF", "OTHER", "SITE_EVIDENCE",
                "COST_ESTIMATE", "OWNER_SUBMISSION", "OWNER_CONFIRMATION",
                "INSPECTION_EVIDENCE", "ISSUE_EVIDENCE", "RECTIFICATION_EVIDENCE",
                "REINSPECTION_EVIDENCE", "SOURCING_REQUIREMENT", "QUOTE_ATTACHMENT",
                "SCHEME_FILE", "DRAWING_FILE", "REVIEW_MINUTES", "RFI_EVIDENCE",
                "DESIGN_RESPONSE", "DISCLOSURE_RECORD", "ACCEPTANCE_ARCHIVE",
                "DELIVERY_NOTE", "MATERIAL_ACCEPTANCE_FORM", "COMMENCEMENT_BASIS",
                "MEASURE_SUPPORT",
                "SECTION_ACCEPTANCE_RECORD", "FINAL_ACCEPTANCE_CERTIFICATE",
                "DEFECT_RECTIFICATION_EVIDENCE", "WARRANTY_RELEASE_VOUCHER",
                "ARCHIVE_TRANSFER_LIST").contains(type)) {
            throw new BusinessException("DOCUMENT_TYPE_INVALID", "不支持的业务文档类型");
        }
        if (Set.of("INVOICE", "SALES_INVOICE").contains(business) && !Set.of("ELECTRONIC_INVOICE", "SCANNED_INVOICE").contains(type)) {
            throw new BusinessException("DOCUMENT_TYPE_MISMATCH", "发票只能上传电子发票或扫描件");
        }
        if ("MATERIAL_RECEIPT".equals(business)
                && !Set.of("DELIVERY_NOTE", "MATERIAL_ACCEPTANCE_FORM").contains(type)) {
            throw new BusinessException("DOCUMENT_TYPE_MISMATCH", "材料验收仅允许送货单或签字验收单");
        }
        if ("PROJECT_COMMENCEMENT".equals(business) && !"COMMENCEMENT_BASIS".equals(type)) {
            throw new BusinessException("DOCUMENT_TYPE_MISMATCH", "开工准入仅允许上传开工依据附件");
        }
        if ("BID_COST".equals(business) && !BID_DOCUMENT_TYPES.contains(type)) {
            throw new BusinessException("DOCUMENT_TYPE_MISMATCH", "投标记录仅允许上传投标业务文件");
        }
        return type;
    }

    private void applyVirusScanStatus(SysFile file, SysFileVO vo) {
        FileVirusScanStatus status;
        try {
            status = FileVirusScanStatus.valueOf(file.getVirusScanStatus());
        } catch (Exception ignored) {
            status = FileVirusScanStatus.NOT_SCANNED;
        }
        vo.setVirusScanStatus(status.name());
        vo.setVirusScanCode(status.code());
        String detail = file.getVirusScanDetail();
        vo.setVirusScanMessage(detail == null || detail.isBlank()
                ? status.message()
                : status.message() + "（" + detail + "）");
        vo.setVirusScanPassed(status.passed());
    }

    public record GeneratedFileArchive(Long fileId, String sha256, int sizeBytes) {
    }
}
