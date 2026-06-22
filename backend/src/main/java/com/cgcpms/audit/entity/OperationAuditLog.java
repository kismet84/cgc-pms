package com.cgcpms.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 实体，映射 sys_operation_audit_log 表。
 * 不含请求体、响应体、Token 或 Cookie 字段。
 */
@Data
@TableName("sys_operation_audit_log")
public class OperationAuditLog {

    @TableId(type = IdType.AUTO)
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
