package com.cgcpms.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkflowSubmitRequest {

    @NotBlank
    private String businessType;

    @NotNull
    private Long businessId;

    @NotBlank
    private String title;

    private BigDecimal amount;

    private Long projectId;

    private Long contractId;

    private String businessSummary;

    private String variables;
}
