package com.cgcpms.site.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("site_daily_log")
public class SiteDailyLog extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    @NotNull private Long projectId;
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") private LocalDate reportDate;
    @NotBlank private String constructionContent;
    private String issuesDelays;
    private String nextDayPlan;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String status;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long submittedBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;
}
