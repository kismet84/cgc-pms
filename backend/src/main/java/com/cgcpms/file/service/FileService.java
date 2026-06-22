package com.cgcpms.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.config.MinioConfig;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.file.vo.SysFileVO;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.cgcpms.common.util.DateTimeUtils;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
public class FileService {

    private static final int PRESIGNED_URL_EXPIRE_MINUTES = 5;

    private final SysFileMapper sysFileMapper;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final com.cgcpms.file.auth.BusinessObjectAuthorizer authorizer;
    private final FileTypeValidator fileTypeValidator = new FileTypeValidator();

    /**
     * Ensure the configured bucket exists on startup.
     */
    @PostConstruct
    public void ensureBucketExists() {
        try {
            String bucket = minioConfig.getBucket();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("Could not ensure MinIO bucket exists: {}", e.getMessage());
        }
    }

    /**
     * Upload a file and associate it with a business entity.
     */
    @Transactional
    public SysFileVO upload(MultipartFile file, String businessType, Long businessId) {
        if (file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "上传文件不能为空");
        }

        // 联合类型校验（扩展名 + MIME + 魔术字节）— 在权限校验之前执行
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException("FILE_EMPTY", "无法读取文件内容");
        }
        FileTypeValidator.ValidationResult vr = fileTypeValidator.validate(
                file.getOriginalFilename(), file.getContentType(), content);

        if (businessType == null || businessType.isBlank()) {
            throw new BusinessException("FILE_PARAM_MISSING", "业务类型不能为空");
        }
        if (businessId == null) {
            throw new BusinessException("FILE_PARAM_MISSING", "业务ID不能为空");
        }
        // 业务对象写权限校验
        authorizer.checkWriteAccess(businessType, businessId);
        // Sanitize businessType to prevent path traversal
        if (!businessType.matches("[A-Za-z0-9_-]+")) {
            throw new BusinessException("FILE_PARAM_INVALID", "业务类型格式非法");
        }

        try {
            String originalName = vr.sanitizedName();
            String fileName = UUID.randomUUID().toString().replace("-", "") + vr.extension();
            String storagePath = businessType + "/" + businessId + "/" + fileName;
            String bucketName = minioConfig.getBucket();
            String contentType = vr.detectedMime();

            // Re-build InputStream since getBytes() consumed it
            java.io.InputStream inputStream = new java.io.ByteArrayInputStream(content);

            // Upload to MinIO — use detectedMime, not client-provided contentType
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storagePath)
                    .stream(inputStream, content.length, -1)
                    .contentType(contentType)
                    .build());

            // Persist file record
            SysFile sysFile = new SysFile();
            sysFile.setTenantId(UserContext.getCurrentTenantId());
            sysFile.setBusinessType(businessType);
            sysFile.setBusinessId(businessId);
            sysFile.setFileName(fileName);
            sysFile.setOriginalName(originalName);
            sysFile.setFileSize((long) content.length);
            sysFile.setContentType(contentType);
            sysFile.setStoragePath(storagePath);
            sysFile.setBucketName(bucketName);
            sysFileMapper.insert(sysFile);

            // Generate presigned URL for response
            String presignedUrl = genPresignedUrl(bucketName, storagePath);
            return toVO(sysFile, presignedUrl);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("File upload failed: businessType={}, businessId={}", businessType, businessId, e);
            throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败，请稍后重试");
        }
    }

    /**
     * Get a presigned download URL for an existing file.
     */
    public String getPresignedUrl(Long fileId) {
        SysFile sysFile = sysFileMapper.selectById(fileId);
        if (sysFile == null) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        if (!sysFile.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        // 业务对象读权限校验
        authorizer.checkReadAccess(sysFile.getBusinessType(), sysFile.getBusinessId());
        try {
            return genPresignedUrl(sysFile.getBucketName(), sysFile.getStoragePath());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for file: {}", fileId, e);
            throw new BusinessException("FILE_URL_ERROR", "获取下载链接失败，请稍后重试");
        }
    }

    /**
     * Delete a file (logical delete in DB + remove from MinIO).
     */
    @Transactional
    public void delete(Long fileId) {
        SysFile sysFile = sysFileMapper.selectById(fileId);
        if (sysFile == null) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        if (!sysFile.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        // 业务对象写权限校验
        authorizer.checkWriteAccess(sysFile.getBusinessType(), sysFile.getBusinessId());

        try {
            // Remove from MinIO first
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(sysFile.getBucketName())
                    .object(sysFile.getStoragePath())
                    .build());
        } catch (Exception e) {
            log.error("Failed to remove file from MinIO: fileId={}, storagePath={}",
                    fileId, sysFile.getStoragePath(), e);
            throw new BusinessException("FILE_DELETE_FAILED", "文件删除失败，请稍后重试");
        }

        // Logical delete in DB
        sysFileMapper.deleteById(fileId);
    }

    /**
     * List files associated with a business entity.
     */
    public List<SysFileVO> listByBusiness(String businessType, Long businessId) {
        if (businessType == null || businessType.isBlank()) {
            throw new BusinessException("FILE_PARAM_MISSING", "业务类型不能为空");
        }
        if (businessId == null) {
            throw new BusinessException("FILE_PARAM_MISSING", "业务ID不能为空");
        }
        // 业务对象读权限校验
        authorizer.checkReadAccess(businessType, businessId);

        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysFile::getBusinessType, businessType)
                .eq(SysFile::getBusinessId, businessId)
                .eq(SysFile::getTenantId, UserContext.getCurrentTenantId())
                .orderByDesc(SysFile::getCreatedAt);

        List<SysFile> files = sysFileMapper.selectList(wrapper);

        return files.stream()
                .map(f -> toVO(f, genPresignedUrl(f.getBucketName(), f.getStoragePath())))
                .toList();
    }

    /**
     * 业务对象读权限校验（供控制器调用）。
     */
    public void checkBizReadPermission(String businessType, Long businessId) {
        authorizer.checkReadAccess(businessType, businessId);
    }

    // ---- private helpers ----

    private String genPresignedUrl(String bucket, String object) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(object)
                            .expiry(PRESIGNED_URL_EXPIRE_MINUTES, TimeUnit.MINUTES)
                            .method(Method.GET)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: bucket={}, object={}", bucket, object, e);
            throw new BusinessException("FILE_URL_ERROR", "生成下载链接失败: " + e.getMessage());
        }
    }

    private SysFileVO toVO(SysFile f, String presignedUrl) {
        SysFileVO vo = new SysFileVO();
        vo.setId(f.getId() == null ? null : String.valueOf(f.getId()));
        vo.setBusinessType(f.getBusinessType());
        vo.setBusinessId(f.getBusinessId() == null ? null : String.valueOf(f.getBusinessId()));
        vo.setFileName(f.getFileName());
        vo.setOriginalName(f.getOriginalName());
        vo.setFileSize(f.getFileSize());
        vo.setContentType(f.getContentType());
        vo.setStoragePath(f.getStoragePath());
        vo.setBucketName(f.getBucketName());
        vo.setPresignedUrl(presignedUrl);
        if (f.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(f.getCreatedAt()));
        return vo;
    }
}
