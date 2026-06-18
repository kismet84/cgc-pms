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

    // ── V36 使用 created_time / updated_time 列名 ──

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    /** 屏蔽 BaseEntity.createdAt（V36 表无 created_at 列） */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(exist = false)
    private LocalDateTime createdAt;

    /** 屏蔽 BaseEntity.updatedAt（V36 表无 updated_at 列） */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(exist = false)
    private LocalDateTime updatedAt;

    private String sellerName;
    private String buyerName;
    private String buyerTaxNo;
    private String sellerTaxNo;

    /** Override BaseEntity.deletedFlag — physical delete for invoices (avoid unique constraint conflicts).
     * <p>
     * 发票实体。使用物理删除（verifyStatus 确认前可删）。
     * 注意：@TableLogic 被 @TableField(exist=false) 覆盖，delete() 执行物理删除。
     * 若需要审计轨迹，请在未来版本中改为软删除，并在唯一约束中加入 deleted_flag。
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(exist = false)
    private Integer deletedFlag;
}
