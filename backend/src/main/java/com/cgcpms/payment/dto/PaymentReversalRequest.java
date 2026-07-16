package com.cgcpms.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentReversalRequest {
    /** REVERSAL=会计冲销，REFUND=银行退款；均使用同一反向编排保证全链守恒。 */
    private String reversalType = "REVERSAL";
    @NotBlank(message = "冲销流水号不能为空")
    private String externalTxnNo;
    @NotNull(message = "冲销时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reversedAt;
    @NotBlank(message = "冲销原因不能为空")
    private String reason;
}
