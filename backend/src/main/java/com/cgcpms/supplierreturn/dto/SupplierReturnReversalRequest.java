package com.cgcpms.supplierreturn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierReturnReversalRequest(@NotBlank @Size(max = 500) String reason) {
}
