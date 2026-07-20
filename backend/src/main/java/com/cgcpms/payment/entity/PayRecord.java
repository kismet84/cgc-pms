package com.cgcpms.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.Version;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_record")
public class PayRecord extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    private Long projectId;

    @NotNull
    private Long payApplicationId;

    private Long contractId;

    private Long partnerId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String recordCode;

    @NotNull
    @Positive
    @Digits(integer = 16, fraction = 2)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal payAmount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payDate;

    private Long fundAccountId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paidAt;

    private String payMethod;

    private String voucherNo;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String payStatus;

    /** 外部交易流水号（唯一，用于幂等）。
     * 数据库层唯一约束已由 V76__fix_pay_record_external_txn_no_unique.sql 添加：
     * UNIQUE KEY uk_external_txn_no (tenant_id, external_txn_no, deleted_flag)。 */
    private String externalTxnNo;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String failureReason;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long reversedRecordId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime reversedAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String reversalType;

    @Version
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer version;
}
