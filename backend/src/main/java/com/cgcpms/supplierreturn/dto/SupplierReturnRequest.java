package com.cgcpms.supplierreturn.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierReturnRequest(
        @NotNull Long receiptItemId,
        @NotBlank @Pattern(regexp = "UNQUALIFIED|ACCEPTED") String returnKind,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull LocalDate returnDate,
        @NotBlank String reason,
        @NotBlank String idempotencyKey) {}
