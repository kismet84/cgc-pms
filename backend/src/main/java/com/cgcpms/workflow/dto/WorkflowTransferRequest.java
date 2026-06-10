package com.cgcpms.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowTransferRequest {

    @NotNull
    private Long targetUserId;

    private String comment;
}
