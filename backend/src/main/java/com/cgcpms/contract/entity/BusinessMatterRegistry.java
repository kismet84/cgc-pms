package com.cgcpms.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("business_matter_registry")
public class BusinessMatterRegistry extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long contractId;
    private String matterKey;
    private String sourceType;
    private Long sourceId;
    private String status;
    private Integer activeToken;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
    private String resolutionNote;
    @Version
    private Integer version;
}
