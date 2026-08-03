package com.cgcpms.bid.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bid_document_version")
public class BidDocumentVersion extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long bidCostId;
    private String documentGroup;
    private String documentType;
    private String logicalName;
    private Integer versionNo;
    private Long supersedesId;
    private Long sysFileId;
    private String status;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long currentToken;
    private String contentSha256;
    private String sourceName;
    private String sourceUrl;
    private LocalDateTime publishedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime submittedAt;
    private String externalReceiptNo;
}
