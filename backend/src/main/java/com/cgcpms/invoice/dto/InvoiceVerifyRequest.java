package com.cgcpms.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class InvoiceVerifyRequest {

    @NotBlank(message = "核验状态不能为空")
    @Pattern(regexp = "VERIFIED|ABNORMAL", message = "核验状态只能为 VERIFIED 或 ABNORMAL")
    private String verifyStatus;
}
