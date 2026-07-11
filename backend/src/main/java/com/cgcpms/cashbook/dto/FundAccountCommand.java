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
public class FundAccountCommand {

    @NotBlank
    @Size(max = 64)
    private String accountCode;

    @NotBlank
    @Size(max = 128)
    private String accountName;

    @NotBlank
    @Pattern(regexp = "CASH|BANK")
    private String accountType;

    @Size(max = 128)
    private String bankName;

    @Size(max = 128)
    private String bankAccountNo;

    @NotNull
    private LocalDate openingDate;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal openingBalance;

    @Size(max = 500)
    private String remark;
}
