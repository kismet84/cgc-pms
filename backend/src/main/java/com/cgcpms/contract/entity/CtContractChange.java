package com.cgcpms.contract.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 合同变更实体。
 *
 * 审计字段设计说明：
 * 当前 Entity 使用 {@code createdTime / updatedTime} 作为 Java 字段名（映射到 DB 列
 * {@code created_at / updated_at}），而非继承 BaseEntity 的 {@code createdAt / updatedAt}。
 * 这是有意为之：CtContractChange 对应的数据库表使用 {@code created_time / updated_time}
 * 列名，与 BaseEntity 的默认映射不同。改为 extends BaseEntity 会因约 15+ 处调用方引用
 * {@code getCreatedTime() / getUpdatedTime()} 而需要大规模重命名，风险高于收益。
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
    private Long id;

    private Long tenantId;

    @NotNull
    private Long projectId;

    @NotNull
    private Long contractId;

    private String changeCode;

    @NotBlank
    private String changeName;

    @NotBlank
    private String changeType;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal beforeAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal changeAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal afterAmount;

    private String reason;

    private String approvalStatus;

    private Integer effectiveFlag;

    private Integer costGeneratedFlag;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    @TableLogic(value = "0", delval = "1")
    private Integer deletedFlag;

    private String remark;
}
