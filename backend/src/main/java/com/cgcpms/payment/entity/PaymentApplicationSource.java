package com.cgcpms.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment_application_source")
public class PaymentApplicationSource extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long payApplicationId;
    @NotBlank(message = "付款来源类型不能为空")
    private String sourceType;
    @NotNull(message = "付款来源不能为空")
    private Long sourceRefId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long expenseId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long settlementId;
    @NotNull(message = "付款来源金额不能为空")
    @Positive(message = "付款来源金额必须大于0")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal sourceAmount;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal paidAmount;
    @Version
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer version;
}
