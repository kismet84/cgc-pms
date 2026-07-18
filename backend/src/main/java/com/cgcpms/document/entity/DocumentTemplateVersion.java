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
@TableName("biz_document_template_version")
public class DocumentTemplateVersion extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long templateId;
    private Integer versionNo;
    private String status;
    private String schemaVersion;
    private String templateContent;
    private String contentHash;
    private String fieldManifest;
    private Long publishedBy;
    private LocalDateTime publishedAt;
}
