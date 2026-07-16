package com.cgcpms.budget.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_budget")
public class ProjectBudget extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    @NotNull(message = "项目不能为空")
    private Long projectId;

    @NotBlank(message = "预算版本号不能为空")
    private String versionNo;

    @NotBlank(message = "预算名称不能为空")
    private String budgetName;

    @NotNull(message = "预算总额不能为空")
    @Positive(message = "预算总额必须大于0")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalAmount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvalStatus;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String status;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer activeFlag;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long activeToken;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime effectiveAt;

    @Version
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer version;
}
