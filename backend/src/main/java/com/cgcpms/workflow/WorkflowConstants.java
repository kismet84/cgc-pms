package com.cgcpms.workflow;

import com.cgcpms.system.dict.util.DictUtils;

/**
 * Workflow constants.
 */
public final class WorkflowConstants {

    private WorkflowConstants() {
    }

    // Instance status
    public static final String INSTANCE_RUNNING = "RUNNING";
    public static final String INSTANCE_APPROVED = "APPROVED";
    public static final String INSTANCE_REJECTED = "REJECTED";
    public static final String INSTANCE_WITHDRAWN = "WITHDRAWN";
    public static final String INSTANCE_VOIDED = "VOIDED";

    // Task status
    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_APPROVED = "APPROVED";
    public static final String TASK_REJECTED = "REJECTED";
    public static final String TASK_CANCELLED = "CANCELLED";
    public static final String TASK_TRANSFERRED = "TRANSFERRED";

    // Node status
    public static final String NODE_WAITING = "WAITING";
    public static final String NODE_ACTIVE = "ACTIVE";
    public static final String NODE_COMPLETED = "COMPLETED";
    public static final String NODE_REJECTED = "REJECTED";
    public static final String NODE_SKIPPED = "SKIPPED";

    // Approve modes
    public static final String MODE_SEQUENTIAL = "SEQUENTIAL";
    public static final String MODE_COUNTERSIGN = "COUNTERSIGN";
    public static final String MODE_OR_SIGN = "OR_SIGN";

    // Action types
    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_WITHDRAW = "WITHDRAW";
    public static final String ACTION_RESUBMIT = "RESUBMIT";
    public static final String ACTION_TRANSFER = "TRANSFER";
    public static final String ACTION_ADD_SIGN = "ADD_SIGN";

    // Available UI actions
    public static final String UI_APPROVE = "approve";
    public static final String UI_REJECT = "reject";
    public static final String UI_WITHDRAW = "withdraw";
    public static final String UI_RESUBMIT = "resubmit";
    public static final String UI_TRANSFER = "transfer";
    public static final String UI_ADD_SIGN = "addSign";

    // Record status
    public static final String RECORD_EFFECTIVE = "EFFECTIVE";
    public static final String RECORD_VOIDED = "VOIDED";

    // Idempotency expiry (hours)
    public static final int IDEMPOTENCY_EXPIRE_HOURS = 24;

    // ── 字典查询方法 ──

    /**
     * 获取工作流实例状态标签
     */
    public static String getInstanceStatusLabel(String value) {
        return DictUtils.getLabelByValue("wf_instance_status", value);
    }

    /**
     * 获取工作流任务状态标签
     */
    public static String getTaskStatusLabel(String value) {
        return DictUtils.getLabelByValue("wf_task_status", value);
    }

    /**
     * 获取工作流节点状态标签
     */
    public static String getNodeStatusLabel(String value) {
        return DictUtils.getLabelByValue("wf_node_status", value);
    }

    /**
     * 获取审批模式标签
     */
    public static String getApproveModeLabel(String value) {
        return DictUtils.getLabelByValue("approve_mode", value);
    }
}
