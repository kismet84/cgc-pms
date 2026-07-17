package com.cgcpms.supplierreturn.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierReturnRequest(
        @NotNull Long receiptItemId,
        Long qualityDispositionId,
        @Pattern(regexp = "UNQUALIFIED|ACCEPTED") String returnKind,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull LocalDate returnDate,
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 128) String idempotencyKey) {
    public SupplierReturnRequest(Long receiptItemId, Long qualityDispositionId, BigDecimal quantity,
                                 LocalDate returnDate, String reason, String idempotencyKey) {
        this(receiptItemId, qualityDispositionId, null, quantity, returnDate, reason, idempotencyKey);
    }

    public SupplierReturnRequest(Long receiptItemId, String returnKind, BigDecimal quantity,
                                 LocalDate returnDate, String reason, String idempotencyKey) {
        this(receiptItemId, null, returnKind, quantity, returnDate, reason, idempotencyKey);
    }
}
