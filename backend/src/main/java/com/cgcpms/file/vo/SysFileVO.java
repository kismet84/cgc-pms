package com.cgcpms.file.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * File information VO, includes presigned download URL.
 */
@Data
public class SysFileVO implements Serializable {

    private String id;
    private String businessType;
    private String documentType;
    private String businessId;
    private String fileName;
    private String originalName;
    private Long fileSize;
    private String contentType;
    private String storagePath;
    private String bucketName;
    private String presignedUrl;
    private String createdAt;
    private String virusScanStatus;
    private String virusScanCode;
    private String virusScanMessage;
    private Boolean virusScanPassed;
}
