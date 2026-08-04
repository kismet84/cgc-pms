package com.cgcpms.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_commencement")
public class ProjectCommencement extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate plannedStartDate;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate actualStartDate;

    private String basisType;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvalStatus;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approvalInstanceId;

    @Version
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer version;
}
