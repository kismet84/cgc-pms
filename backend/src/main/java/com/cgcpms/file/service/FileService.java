package com.cgcpms.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
public class FileService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int PRESIGNED_URL_EXPIRE_DAYS = 7;
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L; // 50 MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",
            ".zip", ".rar", ".7z", ".txt", ".csv"
    );

    private final SysFileMapper sysFileMapper;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * Upload a file and associate it with a business entity.
     */
    @Transactional
    public SysFileVO upload(MultipartFile file, String businessType, Long businessId) {
        if (file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("FILE_TOO_LARGE", "文件大小不能超过 50MB");
        }
        String ext = getExtension(file.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("FILE_TYPE_NOT_ALLOWED", "不支持的文件类型: " + ext);
        }
        if (businessType == null || businessType.isBlank()) {
            throw new BusinessException("FILE_PARAM_MISSING", "业务类型不能为空");
        }
        if (businessId == null) {
            throw new BusinessException("FILE_PARAM_MISSING", "业务ID不能为空");
        }
        // Sanitize businessType to prevent path traversal
        if (!businessType.matches("[A-Za-z0-9_-]+")) {
            throw new BusinessException("FILE_PARAM_INVALID", "业务类型格式非法");
        }

        try {
            String originalName = file.getOriginalFilename();
            String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
            String storagePath = businessType + "/" + businessId + "/" + fileName;
            String bucketName = minioConfig.getBucket();

            // Upload to MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storagePath)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            // Persist file record
            SysFile sysFile = new SysFile();
            sysFile.setBusinessType(businessType);
            sysFile.setBusinessId(businessId);
            sysFile.setFileName(fileName);
            sysFile.setOriginalName(originalName);
            sysFile.setFileSize(file.getSize());
            sysFile.setContentType(file.getContentType());
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

        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysFile::getBusinessType, businessType)
                .eq(SysFile::getBusinessId, businessId)
                .orderByDesc(SysFile::getCreatedAt);

        List<SysFile> files = sysFileMapper.selectList(wrapper);

        return files.stream()
                .map(f -> toVO(f, genPresignedUrl(f.getBucketName(), f.getStoragePath())))
                .toList();
    }

    // ---- private helpers ----

    private String genPresignedUrl(String bucket, String object) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(object)
                            .expiry(PRESIGNED_URL_EXPIRE_DAYS, TimeUnit.DAYS)
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
        if (f.getCreatedAt() != null) vo.setCreatedAt(DTF.format(f.getCreatedAt()));
        return vo;
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.'));
    }
}
