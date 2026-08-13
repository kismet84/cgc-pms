package com.cgcpms.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 采购申请实体 — 对应 V35 mat_purchase_request 表。
 * <p>
 * V35 原使用 created_time / updated_time；V45 已统一为 created_at / updated_at。
 * 为兼容既有 Java 调用方保留 createdTime / updatedTime 属性并显式映射当前列，
 * 同时屏蔽 BaseEntity 的同列属性以避免重复映射。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_purchase_request")
public class MatPurchaseRequest extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    @NotNull(message = "项目不能为空")
    private Long projectId;

    /** 关联采购合同 */
    private Long contractId;

    /** 采购用途/施工部位说明。 */
    private String purpose;

    /** 申请编号，PR-yyyyMMdd-XXX */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String requestCode;

    /** 审批状态：DRAFT草稿，APPROVING审批中，APPROVED已通过，REJECTED已驳回，WITHDRAWN已撤回 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvalStatus;

    /** 业务状态：DRAFT草稿，APPROVED已通过，CONVERTED已转采购订单 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String status;

    // ── V35 原使用 created_time / updated_time，V45 统一重命名为 created_at / updated_at ──

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    /** 屏蔽 BaseEntity.createdAt，当前列由 createdTime 显式映射。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(exist = false)
    private LocalDateTime createdAt;

    /** 屏蔽 BaseEntity.updatedAt，当前列由 updatedTime 显式映射。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}
