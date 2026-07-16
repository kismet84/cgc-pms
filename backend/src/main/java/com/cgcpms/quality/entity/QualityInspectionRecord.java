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
@TableName("qs_inspection_record")
public class QualityInspectionRecord extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long planId;
    @JsonSerialize(using = ToStringSerializer.class) private Long projectId;
    private String inspectionCode;
    private LocalDate inspectionDate;
    private String location;
    @JsonSerialize(using = ToStringSerializer.class) private Long inspectorUserId;
    private String conclusion;
    private String summary;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class) private Long submittedBy;
    private LocalDateTime submittedAt;
    @Version private Integer version;
}
