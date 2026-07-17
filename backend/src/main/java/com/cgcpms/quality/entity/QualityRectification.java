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
@TableName("qs_rectification")
public class QualityRectification extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long issueId;
    @JsonSerialize(using = ToStringSerializer.class) private Long projectId;
    private Integer roundNo;
    private String actionDescription;
    @JsonSerialize(using = ToStringSerializer.class) private Long responsibleUserId;
    private LocalDate plannedCompleteDate;
    private LocalDateTime actualCompletedAt;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class) private Long submittedBy;
    private LocalDateTime submittedAt;
    private String reinspectionComment;
    @JsonSerialize(using = ToStringSerializer.class) private Long reinspectedBy;
    private LocalDateTime reinspectedAt;
    @Version private Integer version;
}
