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
@TableName("qs_inspection_plan")
public class QualityInspectionPlan extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long projectId;
    private String planCode;
    private String planName;
    private String inspectionType;
    private String frequencyType;
    private LocalDate startDate;
    private LocalDate endDate;
    @JsonSerialize(using = ToStringSerializer.class) private Long ownerUserId;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class) private Long activatedBy;
    private LocalDateTime activatedAt;
    @JsonSerialize(using = ToStringSerializer.class) private Long completedBy;
    private LocalDateTime completedAt;
    @Version private Integer version;
}
