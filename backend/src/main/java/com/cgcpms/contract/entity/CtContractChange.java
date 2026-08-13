package com.cgcpms.contract.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 合同变更实体。
 *
 * 审计字段设计说明：
 * 当前 Entity 保留 {@code createdTime / updatedTime} Java 字段名，并显式映射数据库
 * 规范列 {@code created_at / updated_at}，避免破坏既有调用方。
 * MyMetaObjectHandler 已通过 {@code strictInsertFill(metaObject, "createdTime", ...)}
 * 同时支持两种字段名的自动填充。
 *
 * @see com.cgcpms.common.entity.BaseEntity
 * @see com.cgcpms.common.handler.MyMetaObjectHandler
 */
@Data
@TableName("ct_contract_change")
public class CtContractChange implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    @NotNull
    private Long projectId;

    @NotNull
    private Long contractId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String changeCode;

    @NotBlank
    private String changeName;

    /** 合同变更与现场签证共用的业务事项唯一键，用于阻止同一事项跨域重复登记。 */
    private String businessMatterKey;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long sourceVarOrderId;

    @NotBlank
    private String changeType;

    @Digits(integer = 16, fraction = 2)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal beforeAmount;

    @Digits(integer = 16, fraction = 2)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal changeAmount;

    @Digits(integer = 16, fraction = 2)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal afterAmount;

    private String reason;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvalStatus;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer effectiveFlag;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer costGeneratedFlag;

    @TableField(fill = FieldFill.INSERT)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long createdBy;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long updatedBy;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    @TableLogic(value = "0", delval = "1")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer deletedFlag;

    private String remark;
}
