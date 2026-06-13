package com.cgcpms.alert.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Alert log entity — maps to alert_log table created in V24.
 *
 * Does NOT extend BaseEntity because alert_log uses {@code created_time} /
 * {@code updated_time} column names instead of the conventional
 * {@code created_at} / {@code updated_at}.
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

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** Maps to {@code created_time} column. Field named {@code createdAt} so MetaObjectHandler fills it. */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** Maps to {@code updated_time} column. Field named {@code updatedAt} so MetaObjectHandler fills it. */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    @TableField("deleted_flag")
    private Integer deletedFlag;

    private String remark;
}
