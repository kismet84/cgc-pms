package com.cgcpms.invoice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("invoice_payment_allocation")
public class InvoicePaymentAllocation {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long invoiceId;
    @NotNull(message = "付款记录不能为空")
    private Long payRecordId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long payApplicationId;
    @NotNull(message = "分配金额不能为空")
    @Positive(message = "分配金额必须大于0")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal allocatedAmount;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long createdBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;
}
