package com.cgcpms.invoice.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_invoice")
public class PayInvoice extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    private Long payRecordId;

    private Long payApplicationId;

    @NotBlank
    private String invoiceNo;

    @NotBlank
    private String invoiceType;

    @NotNull
    @Positive
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal invoiceAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal taxRate;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal taxAmount;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate invoiceDate;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String verifyStatus;

    /**
     * ── 审计字段 workaround 说明 ──
     *
     * 历史：
     *   V36 最初使用 created_time / updated_time 列名。
     *   V45 将列重命名为 created_at / updated_at（与 BaseEntity 默认映射一致）。
     *
     * 当前状态：
     *   createdTime / updatedTime 显式映射 @TableField(value = "created_at")，
     *   同时 createdAt / updatedAt (@TableField(exist = false)) 屏蔽 BaseEntity
     *   继承的同名字段，避免 MyBatis-Plus 列冲突。
     *
     * 移除时机：
     *   当 InvoiceService.toVO() 及 PayInvoice::getCreatedTime / getUpdatedTime
     *   引用全部迁移为 createdAt / updatedAt 后，即可删除 createdTime / updatedTime
     *   字段及 exist=false 屏蔽字段，直接使用 BaseEntity 的 createdAt / updatedAt。
     */

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    /** 屏蔽 BaseEntity.createdAt，避免与 createdTime 映射到同一 DB 列 created_at */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(exist = false)
    private LocalDateTime createdAt;

    /** 屏蔽 BaseEntity.updatedAt，避免与 updatedTime 映射到同一 DB 列 updated_at */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(exist = false)
    private LocalDateTime updatedAt;

    private String sellerName;
    private String buyerName;
    private String buyerTaxNo;
    private String sellerTaxNo;
}
