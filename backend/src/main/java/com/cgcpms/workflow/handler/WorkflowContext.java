package com.cgcpms.workflow.handler;

import com.cgcpms.workflow.entity.WfInstance;
import lombok.Data;

/**
 * Context object passed to business handlers during workflow state transitions.
 */
@Data
public class WorkflowContext {

    private WfInstance instance;
    private String actionType;
    private String operatorName;
    private String comment;
}
