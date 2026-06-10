package com.cgcpms.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkflowActionRequest {

    @NotBlank
    private String action;

    private String comment;

    @NotBlank
    private String idempotencyKey;
}
