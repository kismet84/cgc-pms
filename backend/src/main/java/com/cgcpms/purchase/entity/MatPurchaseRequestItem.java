package com.cgcpms.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购申请明细实体 — 对应 V35 mat_purchase_request_item 表。
 * <p>
 * V35 原使用 created_time / updated_time；V45 已统一为 created_at / updated_at。
 * 为兼容既有 Java 调用方保留 createdTime / updatedTime 属性并显式映射当前列。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_purchase_request_item")
public class MatPurchaseRequestItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long requestId;

    private Long materialId;

    /** 对应项目进度计划中的 WBS 任务。 */
    private Long wbsTaskId;

    /** 对应已批准项目预算的预算行。 */
    private Long budgetLineId;

    /** 可选分包任务，用于采购与分包履约追溯。 */
    private Long subTaskId;

    /** 材料名称审批快照；无 materialId 时也作为自定义物料输入。 */
    private String materialName;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal quantity;

    /** 当前审批轮次批准数量；原申请数量 quantity 始终保留。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal approvedQuantity;

    /** 审批数量 CAS 版本。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer approvalVersion;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal estimatedUnitPrice;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal estimatedAmount;

    private String unit;

    /** 申请时材料规格快照。 */
    private String specification;

    private String useLocation;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate plannedDate;

    // ── V35 原列经 V45 统一为 created_at / updated_at ──

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
