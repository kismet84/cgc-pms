package com.cgcpms.workflow.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowTemplateNodeReorderRequest {

    @NotEmpty(message = "节点列表不能为空")
    private List<Long> nodeIds;
}
