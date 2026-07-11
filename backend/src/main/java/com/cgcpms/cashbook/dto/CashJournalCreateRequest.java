package com.cgcpms.cashbook.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CashJournalCreateRequest {

    private Long accountId;

    @NotBlank
    @Pattern(regexp = "IN|OUT")
    private String direction;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 16, fraction = 2)
    private BigDecimal amount;

    @NotNull
    private LocalDate businessDate;

    @Size(max = 200)
    private String counterpartyName;

    @NotBlank
    @Size(max = 500)
    private String summary;

    private Long projectId;
    private Long contractId;
}
