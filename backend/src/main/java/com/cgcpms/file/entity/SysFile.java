package com.cgcpms.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * System file entity - generic file storage decoupled from specific business domains.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
public class SysFile extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** Tenant ID */
    private Long tenantId;

    /** Business type (e.g., CONTRACT, PROJECT, PARTNER) */
    private String businessType;

    /** Associated business record ID */
    private Long businessId;

    /** Stored file name (UUID + extension) */
    private String fileName;

    /** Original uploaded file name */
    private String originalName;

    /** File size in bytes */
    private Long fileSize;

    /** MIME content type */
    private String contentType;

    /** MinIO object path (businessType/businessId/fileName) */
    private String storagePath;

    /** MinIO bucket name */
    private String bucketName;

    /** Persisted result of the real malware scan. */
    private String virusScanStatus;

    /** Scanner detail, such as the detected signature name. */
    private String virusScanDetail;

    /** Time at which the scanner produced the persisted result. */
    private LocalDateTime virusScannedAt;
}
