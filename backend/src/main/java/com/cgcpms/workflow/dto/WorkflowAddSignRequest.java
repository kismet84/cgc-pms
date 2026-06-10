package com.cgcpms.workflow.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowAddSignRequest {

    @NotEmpty
    private List<Long> additionalUserIds;

    private String comment;
}
