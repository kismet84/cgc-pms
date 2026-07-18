package com.cgcpms.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_document_generation")
public class DocumentGeneration extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String generationNo;
    private String businessType;
    private Long businessId;
    private Long templateId;
    private Long templateVersionId;
    private String schemaVersion;
    private String sourceDigest;
    private String outputSha256;
    private String rendererId;
    private String rendererVersion;
    private String status;
    private Long fileId;
    private String idempotencyKey;
    private Long retryOfGenerationId;
    private String failureCode;
    private Long requestedBy;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
}
