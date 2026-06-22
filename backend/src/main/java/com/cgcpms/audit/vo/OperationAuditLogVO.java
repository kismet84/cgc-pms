package com.cgcpms.audit.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志查询 VO — 不含敏感字段。
 */
@Data
public class OperationAuditLogVO {

    private Long id;

    private Long tenantId;

    private Long userId;

    private String operationType;

    private String businessType;

    private String businessId;

    private String httpMethod;

    private String requestPath;

    private Integer successFlag;

    private String errorCode;

    private String sourceIp;

    private Integer durationMs;

    private LocalDateTime createdAt;
}
