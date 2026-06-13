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
import java.time.LocalDateTime;

/**
 * 项目成员表实体 — 对应 V34 pm_project_member 表。
 * <p>
 * V34 使用 created_time / updated_time 列名，与 BaseEntity 默认的 created_at / updated_at
 * 不同。因此新增 createdTime / updatedTime 字段并显式映射，同时将 BaseEntity 的
 * createdAt / updatedAt 标记为 exist=false 以避免映射冲突。
 * <p>
 * 其他审计字段 (created_by, updated_by, deleted_flag, remark) 列名与 BaseEntity 默认
 * 驼峰→下划线映射一致，直接继承使用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_project_member")
public class PmProjectMember extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @NotNull
    private Long tenantId;

    @NotNull
    private Long projectId;

    @NotNull
    private Long userId;

    @NotBlank
    private String roleCode;

    private String positionName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String status;

    // ── V34 使用 created_time / updated_time 列名 ──

    @TableField("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableField("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    /** 屏蔽 BaseEntity.createdAt（V34 表无 created_at 列） */
    @TableField(exist = false)
    private LocalDateTime createdAt;

    /** 屏蔽 BaseEntity.updatedAt（V34 表无 updated_at 列） */
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}
