package com.cgcpms.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentFailureRequest {
    @NotNull private Long payApplicationId;
    @NotNull @Positive @Digits(integer = 16, fraction = 2) private BigDecimal payAmount;
    @NotBlank private String externalTxnNo;
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime attemptedAt;
    @NotBlank private String failureReason;
    private Long fundAccountId;
    private String payMethod;
}
