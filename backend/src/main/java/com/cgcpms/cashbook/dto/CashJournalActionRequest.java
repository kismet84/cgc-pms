package com.cgcpms.cashbook.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CashJournalActionRequest {
    @Size(max = 500)
    private String reason;
}
