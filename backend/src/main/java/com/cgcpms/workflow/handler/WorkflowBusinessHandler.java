package com.cgcpms.workflow.handler;

/**
 * Business callback handler invoked by the workflow engine when
 * an approval instance changes state.
 */
public interface WorkflowBusinessHandler {

    String supportBusinessType();

    default void beforeSubmit(WorkflowContext context) {
    }

    default void onRunning(WorkflowContext context) {
    }

    default void onApproved(WorkflowContext context) {
    }

    default void onRejected(WorkflowContext context) {
    }

    default void onWithdrawn(WorkflowContext context) {
    }

    default void onVoided(WorkflowContext context) {
    }
}
