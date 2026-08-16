package com.cgcpms.workflow.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.WorkflowSecurityPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.role.SystemRoleContract;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Handles approve and reject workflow operations.
 */
@Service
@RequiredArgsConstructor
public class WorkflowApprovalService {

    private static final ObjectMapper POLICY_MAPPER = new ObjectMapper();
    private final WorkflowCoreService core;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfNodeInstanceMapper wfNodeInstanceMapper;
    private final WfTaskMapper wfTaskMapper;
    private final SysUserMapper sysUserMapper;
    private final WorkflowNotificationAlertService workflowNotificationAlertService;

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long taskId, Long userId, String username,
                        String comment, String idempotencyKey) {
        approve(taskId, userId, username, comment, idempotencyKey, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void approvePurchaseRequest(Long taskId, Long userId, String username,
                                       String comment, String idempotencyKey) {
        approve(taskId, userId, username, comment, idempotencyKey, true);
    }

    private void approve(Long taskId, Long userId, String username,
                         String comment, String idempotencyKey, boolean purchaseRequestDedicated) {
        ApprovalRoute route = lockApprovalRoute(taskId, userId);
        requireFinanceApprover(route.instance(), userId);
        WfTask routeTask = route.task();
        if (routeTask != null && com.cgcpms.workflow.WorkflowBusinessTypes.PURCHASE_REQUEST
                .equals(routeTask.getBusinessType()) && !purchaseRequestDedicated) {
            throw new BusinessException("PURCHASE_REQUEST_DEDICATED_APPROVAL_REQUIRED",
                    "采购申请同意必须通过采购申请专用审批入口");
        }
        WfTask task = validateAndCasUpdateTask(route.task(), userId, idempotencyKey,
                WorkflowConstants.ACTION_APPROVE, WorkflowConstants.TASK_APPROVED, comment);
        WfInstance instance = route.instance();
        WorkflowSecurityPolicy policy = WorkflowSecurityPolicy.parseOrLegacy(
                POLICY_MAPPER, instance.getSecurityPolicyJson());
        if ((policy.preventInitiatorApproval() || WorkflowSecurityPolicy.requiresFinanceSeparation(instance.getBusinessType()))
                && instance.getInitiatorId().equals(userId)) {
            throw new BusinessException("WORKFLOW_INITIATOR_APPROVAL_FORBIDDEN", "发起人不得审批本流程");
        }
        if (core.approvedCount(instance.getTenantId(), instance.getId(), userId, task.getRoundNo())
                > policy.maxApprovalsPerUser()) {
            throw new BusinessException("WORKFLOW_APPROVAL_LIMIT_EXCEEDED", "同一用户审批次数超过流程安全策略");
        }

        // Write record
        WfNodeInstance nodeInstance = route.node();
        core.writeRecord(task.getTenantId(), task.getBusinessType(), task.getBusinessId(),
                task.getInstanceId(), task.getNodeInstanceId(), taskId, task.getRoundNo(),
                nodeInstance != null ? nodeInstance.getNodeCode() : null,
                nodeInstance != null ? nodeInstance.getNodeName() : null,
                WorkflowConstants.ACTION_APPROVE, "同意",
                userId, username, comment);

        // Notify submitter
        if (instance != null) {
            workflowNotificationAlertService.createWorkflowNotification(instance,
                    instance.getInitiatorId(),
                    username + "同意了你的申请",
                    username + "同意了你的申请：" + instance.getTitle(),
                    "APPROVAL_COMPLETED");
        }

        // Check if node is complete
        String approveMode = nodeInstance != null ? nodeInstance.getApproveMode() : WorkflowConstants.MODE_SEQUENTIAL;
        if (core.isNodeComplete(task.getTenantId(), task.getNodeInstanceId(), approveMode)) {
            // For OR_SIGN: cancel remaining pending tasks before proceeding
            if (WorkflowConstants.MODE_OR_SIGN.equals(approveMode)) {
                core.cancelOrSignPendingTasks(task.getNodeInstanceId(), taskId);
            }

            // Mark node completed
            core.completeNode(task.getNodeInstanceId());

            // Find next waiting node
            WfNodeInstance nextNode = core.findNextWaitingNode(instance.getId(), instance.getCurrentRound());

            if (nextNode != null) {
                core.activateNode(nextNode, userId, username, instance.getTenantId());
            } else {
                // All nodes complete → instance approved
                instance.setInstanceStatus(WorkflowConstants.INSTANCE_APPROVED);
                instance.setEndedAt(LocalDateTime.now());
                wfInstanceMapper.updateById(instance);

                core.writeRecord(instance.getTenantId(), instance.getBusinessType(), instance.getBusinessId(),
                        instance.getId(), null, null, instance.getCurrentRound(),
                        null, null, WorkflowConstants.ACTION_APPROVE, "审批通过",
                        userId, username, "所有节点审批通过");

                core.notifyHandler(instance.getBusinessType(), instance,
                        WorkflowConstants.ACTION_APPROVE, username, null);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(Long taskId, Long userId, String username,
                       String comment, String idempotencyKey) {
        ApprovalRoute route = lockApprovalRoute(taskId, userId);
        WfInstance routeInstance = route.instance();
        requireFinanceApprover(routeInstance, userId);
        if (routeInstance != null && WorkflowSecurityPolicy.requiresFinanceSeparation(routeInstance.getBusinessType())
                && routeInstance.getInitiatorId().equals(userId)) {
            throw new BusinessException("WORKFLOW_INITIATOR_APPROVAL_FORBIDDEN", "发起人不得驳回本流程");
        }
        WfTask task = validateAndCasUpdateTask(route.task(), userId, idempotencyKey,
                WorkflowConstants.ACTION_REJECT, WorkflowConstants.TASK_REJECTED, comment);

        // Cancel other pending tasks in the same node
        core.cancelPendingTasksInNode(task.getNodeInstanceId(), taskId);

        // Mark node rejected
        WfNodeInstance nodeInstance = route.node();
        if (nodeInstance != null) {
            nodeInstance.setNodeStatus(WorkflowConstants.NODE_REJECTED);
            nodeInstance.setEndedAt(LocalDateTime.now());
            wfNodeInstanceMapper.updateById(nodeInstance);
        }

        // Mark instance rejected
        WfInstance instance = route.instance();
        instance.setInstanceStatus(WorkflowConstants.INSTANCE_REJECTED);
        instance.setEndedAt(LocalDateTime.now());
        wfInstanceMapper.updateById(instance);

        core.writeRecord(task.getTenantId(), task.getBusinessType(), task.getBusinessId(),
                task.getInstanceId(), task.getNodeInstanceId(), taskId, task.getRoundNo(),
                nodeInstance != null ? nodeInstance.getNodeCode() : null,
                nodeInstance != null ? nodeInstance.getNodeName() : null,
                WorkflowConstants.ACTION_REJECT, "驳回",
                userId, username, comment);

        core.notifyHandler(instance.getBusinessType(), instance,
                WorkflowConstants.ACTION_REJECT, username, comment);

        // Notify submitter
        workflowNotificationAlertService.createWorkflowNotification(instance, instance.getInitiatorId(),
                username + "驳回了你的申请",
                username + "驳回了你的申请：" + instance.getTitle(),
                "APPROVAL_REJECTED");
    }

    // ──────────────────────── Extracted helpers ────────────────────────

    private void requireFinanceApprover(WfInstance instance, Long userId) {
        if (instance == null || !WorkflowSecurityPolicy.requiresFinanceSeparation(instance.getBusinessType())) return;
        if (!sysUserMapper.selectEnabledRoleCodesByTenantAndUserId(instance.getTenantId(), userId)
                .contains(SystemRoleContract.COMPANY_FINANCE)) {
            throw new BusinessException("WORKFLOW_FINANCE_APPROVER_REQUIRED", "仅公司财务可审批本流程");
        }
    }

    private ApprovalRoute lockApprovalRoute(Long taskId, Long userId) {
        WfTask task = wfTaskMapper.selectByIdIgnoringTenant(taskId);
        if (task == null) throw new BusinessException("TASK_NOT_FOUND", "审批任务不存在");
        core.requireCurrentTenant(task.getTenantId());
        if (!WorkflowConstants.TASK_PENDING.equals(task.getTaskStatus()))
            throw new BusinessException("TASK_ALREADY_HANDLED", "该任务已被处理");
        if (!task.getApproverId().equals(userId))
            throw new BusinessException("NOT_TASK_OWNER", "非当前任务审批人");
        if (wfInstanceMapper.pingInstanceRunning(task.getInstanceId(), WorkflowConstants.INSTANCE_RUNNING) != 1)
            throw new BusinessException("INSTANCE_STATUS_CONFLICT", "审批实例状态已变更，无法处理任务");
        WfInstance instance = wfInstanceMapper.selectByIdForUpdate(task.getInstanceId(), task.getTenantId());
        WfNodeInstance node = wfNodeInstanceMapper.selectByIdForUpdate(task.getNodeInstanceId(), task.getTenantId());
        WfTask lockedTask = wfTaskMapper.selectByIdForUpdate(taskId, task.getTenantId());
        if (instance == null || node == null || !WorkflowConstants.NODE_ACTIVE.equals(node.getNodeStatus()))
            throw new BusinessException("NODE_NOT_ACTIVE", "只能处理当前活动审批节点");
        if (lockedTask == null) throw new BusinessException("TASK_NOT_FOUND", "审批任务不存在");
        if (!WorkflowConstants.TASK_PENDING.equals(lockedTask.getTaskStatus()))
            throw new BusinessException("TASK_ALREADY_HANDLED", "该任务已被处理");
        if (!lockedTask.getApproverId().equals(userId))
            throw new BusinessException("NOT_TASK_OWNER", "非当前任务审批人");
        core.requireEligibleApprover(node, instance, userId);
        return new ApprovalRoute(lockedTask, instance, node);
    }

    private record ApprovalRoute(WfTask task, WfInstance instance, WfNodeInstance node) {}

    /**
     * Validates task: existence, PENDING status, ownership, idempotency.
     * Performs CAS update to atomically claim the task.
     * Returns the freshly-loaded task for downstream use.
     */
    private WfTask validateAndCasUpdateTask(WfTask task, Long userId, String idempotencyKey,
                                              String actionType, String targetStatus, String comment) {
        if (!WorkflowConstants.TASK_PENDING.equals(task.getTaskStatus())) {
            throw new BusinessException("TASK_ALREADY_HANDLED", "该任务已被处理");
        }
        if (!task.getApproverId().equals(userId)) {
            throw new BusinessException("NOT_TASK_OWNER", "非当前任务审批人");
        }
        core.checkIdempotency(task.getTenantId(), userId, idempotencyKey);

        // CAS update: atomically check PENDING + version, bump version
        int updated = wfTaskMapper.updateTaskStatusWithCas(
                task.getId(),
                WorkflowConstants.TASK_PENDING,
                task.getTaskVersion(),
                targetStatus,
                actionType,
                comment,
                LocalDateTime.now(),
                task.getTenantId());
        if (updated != 1) {
            throw new BusinessException("TASK_VERSION_CONFLICT", "任务已被他人处理（乐观锁冲突），请刷新后重试");
        }

        return task;
    }
}
