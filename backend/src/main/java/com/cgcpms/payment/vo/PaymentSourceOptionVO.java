package com.cgcpms.payment.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentSourceOptionVO {
    private String sourceType;
    private String sourceRefId;
    private String documentCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal sourceTotalAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal committedAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal availableAmount;
}
