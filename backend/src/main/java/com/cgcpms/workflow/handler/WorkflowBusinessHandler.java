package com.cgcpms.workflow.handler;

/**
 * Business callback handler invoked by the workflow engine when
 * an approval instance changes state.
 *
 * <h3>实现指南</h3>
 * <p>各业务类型的 handler 必须覆盖其对应状态的回调方法，否则关键状态变更将静默丢弃。</p>
 * <table border="1">
 *   <caption>业务类型与回调覆盖要求</caption>
 *   <tr><th>业务类型</th><th>必须覆盖</th><th>建议覆盖</th></tr>
 *   <tr><td>CONTRACT_APPROVAL</td><td>onApproved, onRejected, onWithdrawn</td><td>onRunning</td></tr>
 *   <tr><td>PAY_REQUEST</td><td>onApproved, onRejected</td><td>onWithdrawn</td></tr>
 *   <tr><td>MAT_RECEIPT</td><td>onApproved, onRejected</td><td>onVoided</td></tr>
 *   <tr><td>SUB_MEASURE</td><td>onApproved, onRejected</td><td>onVoided</td></tr>
 *   <tr><td>VAR_ORDER</td><td>onApproved, onRejected</td><td>onVoided</td></tr>
 *   <tr><td>STL_SETTLEMENT</td><td>onApproved, onRejected</td><td>onVoided</td></tr>
 *   <tr><td>PURCHASE</td><td>onApproved, onRejected, onWithdrawn</td><td>onRunning</td></tr>
 * </table>
 * <p>
 * 未覆盖的 default 回调方法体为空，不会产生副作用。
 * 所有回调均为事务性调用，异常会触发回滚（当 {@link #isCritical()} 返回 true 时）。
 * </p>
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

    /**
     * Whether this handler's callbacks are critical for transactional consistency.
     * If true, exceptions from callbacks will propagate and trigger rollback.
     * If false (default), exceptions are logged but swallowed.
     */
    default boolean isCritical() {
        return false;
    }
}
