package com.cgcpms.materialreturn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MaterialReturnReversalRequest(
        @NotBlank @Size(max = 500) String reason) {
}
