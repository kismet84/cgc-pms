package com.cgcpms.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qs_issue")
public class QualitySafetyIssue extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long planId;
    @JsonSerialize(using = ToStringSerializer.class) private Long inspectionId;
    @JsonSerialize(using = ToStringSerializer.class) private Long projectId;
    private String issueCode;
    private String issueType;
    private String category;
    private String severity;
    private String title;
    private String description;
    private String responsibleKind;
    @JsonSerialize(using = ToStringSerializer.class) private Long responsiblePartnerId;
    @JsonSerialize(using = ToStringSerializer.class) private Long responsibleUserId;
    private LocalDate dueDate;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class) private Long closedBy;
    private LocalDateTime closedAt;
    @Version private Integer version;
}
