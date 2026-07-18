package com.cgcpms.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_document_default_binding")
public class DocumentDefaultBinding {
    private Long tenantId;
    @TableId(type = IdType.INPUT)
    private String businessType;
    private Long templateId;
    private Long templateVersionId;
    private Integer lockVersion;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
