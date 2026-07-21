package com.cgcpms.site.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.cgcpms.site.json.StrictIntegerDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 200, message = "天气摘要最多 200 字")
    private String weatherSummary;
    @Min(value = 0, message = "在场人数不能为负数")
    @Max(value = 100000, message = "在场人数不能超过 100000")
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer onSiteHeadcount;
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expectedUpdatedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String status;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long submittedBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;
}
