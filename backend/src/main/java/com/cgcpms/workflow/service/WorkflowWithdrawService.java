package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles withdraw workflow operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowWithdrawService {

    private final WorkflowCoreService core;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfTaskMapper wfTaskMapper;

    @Transactional
    public void withdraw(Long instanceId, Long userId, String username) {

        WfInstance instance = wfInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException("INSTANCE_NOT_FOUND", "审批实例不存在");
        }
        if (!WorkflowConstants.INSTANCE_RUNNING.equals(instance.getInstanceStatus())) {
            throw new BusinessException("INSTANCE_NOT_RUNNING", "只能撤回运行中的审批");
        }
        if (!instance.getInitiatorId().equals(userId)) {
            throw new BusinessException("NOT_INITIATOR", "只有发起人可以撤回");
        }

        // Notify pending approvers before cancelling tasks
        List<WfTask> pendingTasks = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instanceId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        for (WfTask t : pendingTasks) {
            try {
                core.notificationService.create(instance.getTenantId(), t.getApproverId(),
                        username + "撤回了审批",
                        username + "撤回了审批：" + instance.getTitle(),
                        "WORKFLOW", instanceId);
            } catch (Exception e) {
                log.warn("Failed to create withdraw notification for approver {}: {}", t.getApproverId(), e.getMessage());
            }
        }

        core.cancelAllPendingTasks(instanceId);
        core.resetActiveNodes(instanceId);

        // CAS: atomically transition instance from RUNNING → WITHDRAWN,
        // but only if no task has been APPROVED or REJECTED (no concurrent approve won).
        int updated = wfInstanceMapper.updateInstanceStatusWithCasNoApprovedTasks(
                instanceId,
                WorkflowConstants.INSTANCE_RUNNING,
                WorkflowConstants.INSTANCE_WITHDRAWN,
                LocalDateTime.now());
        if (updated != 1) {
            throw new BusinessException("INSTANCE_STATUS_CONFLICT", "审批实例状态已变更或已有任务被处理，撤回失败");
        }

        core.writeRecord(instance.getTenantId(), instance.getBusinessType(), instance.getBusinessId(),
                instanceId, null, null, instance.getCurrentRound(),
                null, null, WorkflowConstants.ACTION_WITHDRAW, "撤回",
                userId, username, null);

        core.notifyHandler(instance.getBusinessType(), instance,
                WorkflowConstants.ACTION_WITHDRAW, username, null);
    }
}
