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
 * TODO(G1): 应 extends BaseEntity 而非 implements Serializable。
 * 当前 Entity 的审计字段 (createdBy/createdTime/updatedBy/updatedTime/deletedFlag/remark)
 * 使用了 {@code createdTime / updatedTime} 作为 Java 字段名，而 BaseEntity 使用
 * {@code createdAt / updatedAt}。两者映射到相同的 DB 列 ({@code created_at / updated_at})，
 * 但 Java 字段名不一致。切换前需全局搜索并替换所有对 {@code getCreatedTime()}
 * 和 {@code getUpdatedTime()} 的引用。
 *
 * @see com.cgcpms.common.entity.BaseEntity
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
