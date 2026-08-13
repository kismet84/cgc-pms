package com.cgcpms.contract.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateContractChangeRequest(
        @NotNull Long projectId,
        @NotNull Long contractId,
        @NotBlank String changeName,
        String businessMatterKey,
        @NotBlank String changeType,
        @Digits(integer = 16, fraction = 2) BigDecimal beforeAmount,
        @Digits(integer = 16, fraction = 2) BigDecimal changeAmount,
        @Digits(integer = 16, fraction = 2) BigDecimal afterAmount,
        String reason,
        String remark) {
}
