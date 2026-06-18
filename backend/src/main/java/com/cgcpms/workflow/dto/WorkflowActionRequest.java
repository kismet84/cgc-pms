package com.cgcpms.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowActionRequest {

    @NotBlank
    private String action;

    private String comment;

    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;
}
