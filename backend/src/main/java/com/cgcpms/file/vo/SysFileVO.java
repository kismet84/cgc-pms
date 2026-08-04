package com.cgcpms.file.vo;

import lombok.Data;

import java.io.Serializable;

/** Public file metadata. Storage internals and bearer URLs are intentionally excluded. */
@Data
public class SysFileVO implements Serializable {

    private String id;
    private String businessType;
    private String documentType;
    private String businessId;
    private String originalName;
    private Long fileSize;
    private String contentType;
    private String createdAt;
    private String virusScanStatus;
    private String virusScanCode;
    private String virusScanMessage;
    private Boolean virusScanPassed;
}
