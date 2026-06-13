package com.cgcpms.workflow.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Handles approve and reject workflow operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowApprovalService {

    private final WorkflowCoreService core;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfNodeInstanceMapper wfNodeInstanceMapper;
    private final WfTaskMapper wfTaskMapper;

    @Transactional
    public void approve(Long taskId, Long userId, String username,
                        String comment, String idempotencyKey) {
        WfTask task = wfTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("TASK_NOT_FOUND", "审批任务不存在");
        }
        if (!WorkflowConstants.TASK_PENDING.equals(task.getTaskStatus())) {
            throw new BusinessException("TASK_ALREADY_HANDLED", "该任务已被处理");
        }
        if (!task.getApproverId().equals(userId)) {
            throw new BusinessException("NOT_TASK_OWNER", "非当前任务审批人");
        }
        core.checkIdempotency(task.getTenantId(), userId, idempotencyKey, WorkflowConstants.ACTION_APPROVE);

        // Update task
        task.setTaskStatus(WorkflowConstants.TASK_APPROVED);
        task.setActionType(WorkflowConstants.ACTION_APPROVE);
        task.setComment(comment);
        task.setHandledAt(LocalDateTime.now());
        int updated = wfTaskMapper.updateById(task);
        if (updated == 0) {
            throw new BusinessException("TASK_VERSION_CONFLICT", "任务已被他人处理（乐观锁冲突），请刷新后重试");
        }

        // Write record
        WfNodeInstance nodeInstance = wfNodeInstanceMapper.selectById(task.getNodeInstanceId());
        core.writeRecord(task.getTenantId(), task.getBusinessType(), task.getBusinessId(),
                task.getInstanceId(), task.getNodeInstanceId(), taskId, task.getRoundNo(),
                nodeInstance != null ? nodeInstance.getNodeCode() : null,
                nodeInstance != null ? nodeInstance.getNodeName() : null,
                WorkflowConstants.ACTION_APPROVE, "同意",
                userId, username, comment);

        // Notify submitter
        try {
            WfInstance instanceForNotify = wfInstanceMapper.selectById(task.getInstanceId());
            if (instanceForNotify != null) {
                core.notificationService.create(instanceForNotify.getTenantId(), instanceForNotify.getInitiatorId(),
                        username + "同意了你的申请",
                        username + "同意了你的申请：" + instanceForNotify.getTitle(),
                        "WORKFLOW", instanceForNotify.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to create approve notification: {}", e.getMessage());
        }

        // Check if node is complete
        String approveMode = nodeInstance != null ? nodeInstance.getApproveMode() : WorkflowConstants.MODE_SEQUENTIAL;
        if (core.isNodeComplete(task.getNodeInstanceId(), approveMode)) {
            // Mark node completed
            core.completeNode(task.getNodeInstanceId());

            // Find next waiting node
            WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
            WfNodeInstance nextNode = core.findNextWaitingNode(instance.getId(), instance.getCurrentRound());

            if (nextNode != null) {
                // Activate next node
                WfTemplateNode nextTplNode = core.wfTemplateNodeMapper.selectById(nextNode.getTemplateNodeId());
                core.activateNode(nextNode, nextTplNode, userId, username,
                        instance.getTenantId());
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

    @Transactional
    public void reject(Long taskId, Long userId, String username,
                       String comment, String idempotencyKey) {
        WfTask task = wfTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("TASK_NOT_FOUND", "审批任务不存在");
        }
        if (!WorkflowConstants.TASK_PENDING.equals(task.getTaskStatus())) {
            throw new BusinessException("TASK_ALREADY_HANDLED", "该任务已被处理");
        }
        if (!task.getApproverId().equals(userId)) {
            throw new BusinessException("NOT_TASK_OWNER", "非当前任务审批人");
        }
        core.checkIdempotency(task.getTenantId(), userId, idempotencyKey, WorkflowConstants.ACTION_REJECT);

        task.setTaskStatus(WorkflowConstants.TASK_REJECTED);
        task.setActionType(WorkflowConstants.ACTION_REJECT);
        task.setComment(comment);
        task.setHandledAt(LocalDateTime.now());
        int updated = wfTaskMapper.updateById(task);
        if (updated == 0) {
            throw new BusinessException("TASK_VERSION_CONFLICT", "任务已被他人处理（乐观锁冲突）");
        }

        // Cancel other pending tasks in the same node
        core.cancelPendingTasksInNode(task.getNodeInstanceId(), taskId);

        // Mark node rejected
        WfNodeInstance nodeInstance = wfNodeInstanceMapper.selectById(task.getNodeInstanceId());
        if (nodeInstance != null) {
            nodeInstance.setNodeStatus(WorkflowConstants.NODE_REJECTED);
            nodeInstance.setEndedAt(LocalDateTime.now());
            wfNodeInstanceMapper.updateById(nodeInstance);
        }

        // Mark instance rejected
        WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
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
        try {
            core.notificationService.create(instance.getTenantId(), instance.getInitiatorId(),
                    username + "驳回了你的申请",
                    username + "驳回了你的申请：" + instance.getTitle(),
                    "WORKFLOW", instance.getId());
        } catch (Exception e) {
            log.warn("Failed to create reject notification: {}", e.getMessage());
        }
    }
}
