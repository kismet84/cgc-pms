package com.cgcpms.alert.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Alert log entity — maps to alert_log table created in V24.
 *
 * Does NOT extend BaseEntity because alert_log uses explicit
 * {@code created_at} / {@code updated_at} mapping with custom fill strategy.
 */
@Data
@TableName("alert_log")
public class AlertLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long projectId;

    /**
     * 关联合同ID（允许为空，因部分规则如动态成本超标仅按项目维度预警）
     */
    @TableField("contract_id")
    private Long contractId;

    /** 业务分类: COST / CONTRACT / PURCHASE / PAYMENT / VARIATION. */
    private String alertDomain;

    /** 细分类标签：如 PURCHASE_DELIVERY / CONTRACT_TERM. */
    private String alertCategory;

    /** 最小跳转定位类型，如 PURCHASE_ORDER. */
    private String sourceType;

    /** 最小跳转定位 ID. */
    private Long sourceId;

    /** 去重键：用于 M2 规则治理增强后的最小抑制 / 去重闭环。 */
    private String dedupKey;

    /** Rule type identifier, e.g. {@code DYNAMIC_COST_EXCEEDS_TARGET}. */
    private String ruleType;

    /** Severity: HIGH / MEDIUM / LOW. */
    private String severity;

    /** Human-readable alert message. */
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime triggeredAt;

    /** 0 = unread, 1 = read. */
    private Integer isRead;

    /** OPEN / PROCESSED / ARCHIVED / INVALID. */
    private String processStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime archivedAt;

    /** 状态备注：处理说明、归档原因、失效原因等。 */
    private String statusRemark;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** Maps to {@code created_at} column. Field named {@code createdAt} so MetaObjectHandler fills it. */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** Maps to {@code updated_at} column. Field named {@code updatedAt} so MetaObjectHandler fills it. */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    @TableField("deleted_flag")
    private Integer deletedFlag;

    private String remark;

    @JsonProperty("bizType")
    public String getBizType() {
        return sourceType;
    }

    @JsonProperty("bizId")
    public Long getBizId() {
        return sourceId;
    }

    @JsonProperty("businessType")
    public String getBusinessType() {
        return sourceType;
    }

    @JsonProperty("businessId")
    public Long getBusinessId() {
        return sourceId;
    }

    @JsonProperty("handledStatus")
    public String getHandledStatus() {
        return processStatus;
    }

    @JsonProperty("handledBy")
    public Long getHandledBy() {
        return updatedBy;
    }

    @JsonProperty("handledAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public LocalDateTime getHandledAt() {
        if ("ARCHIVED".equals(processStatus) || "INVALID".equals(processStatus)) {
            return archivedAt != null ? archivedAt : processedAt;
        }
        return processedAt != null ? processedAt : archivedAt;
    }
}
