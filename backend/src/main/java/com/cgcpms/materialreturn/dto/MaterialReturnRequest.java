package com.cgcpms.materialreturn.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MaterialReturnRequest(
        @NotNull Long requisitionItemId,
        @NotNull Long originalStockTxnId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull LocalDate returnDate,
        @NotBlank String reason,
        @NotBlank String idempotencyKey) {}
