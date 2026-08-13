package com.cgcpms.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 项目成员表实体 — 对应 V34 pm_project_member 表。
 * <p>
 * V45 已将 V34 的 created_time / updated_time 统一为 created_at / updated_at，
 * 审计字段直接使用 BaseEntity 映射与只读 JSON 契约。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_project_member")
public class PmProjectMember extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("project_id")
    private Long projectId;

    @NotNull
    @TableField("user_id")
    private Long userId;

    @NotBlank
    @TableField("role_code")
    private String roleCode;

    @TableField("position_name")
    private String positionName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("start_date")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("end_date")
    private LocalDate endDate;

    private String status;
}
