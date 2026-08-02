package com.cgcpms.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购申请明细实体 — 对应 V35 mat_purchase_request_item 表。
 * <p>
 * V35 使用 created_time / updated_time 列名，与 BaseEntity 默认的 created_at / updated_at
 * 不同。因此新增 createdTime / updatedTime 字段并显式映射，同时将 BaseEntity 的
 * createdAt / updatedAt 标记为 exist=false 以避免映射冲突。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_purchase_request_item")
public class MatPurchaseRequestItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    @NotNull
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
    private BigDecimal approvedQuantity;

    /** 审批数量 CAS 版本。 */
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

    // ── V35 使用 created_time / updated_time 列名 ──

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    /** 屏蔽 BaseEntity.createdAt（V35 表无 created_at 列） */
    @TableField(exist = false)
    private LocalDateTime createdAt;

    /** 屏蔽 BaseEntity.updatedAt（V35 表无 updated_at 列） */
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}
