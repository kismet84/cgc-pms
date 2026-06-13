package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Facade for the workflow engine. Delegates to focused sub-services:
 * {@link WorkflowSubmitService}, {@link WorkflowApprovalService},
 * {@link WorkflowTaskService}, {@link WorkflowWithdrawService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final WorkflowSubmitService submitService;
    private final WorkflowApprovalService approvalService;
    private final WorkflowTaskService taskService;
    private final WorkflowWithdrawService withdrawService;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfTaskMapper wfTaskMapper;

    // ───────────────────── SUBMIT ─────────────────────

    public WfInstance submit(Long userId, String username, Long tenantId,
                             String businessType, Long businessId,
                             String title, java.math.BigDecimal amount,
                             Long projectId, Long contractId,
                             String businessSummary, String variables,
                             List<Long> ccUserIds) {
        return submitService.submit(userId, username, tenantId,
                businessType, businessId, title, amount,
                projectId, contractId, businessSummary, variables, ccUserIds);
    }

    // ───────────────────── RESUBMIT ─────────────────────

    public WfInstance resubmit(Long instanceId, Long userId, String username) {
        return submitService.resubmit(instanceId, userId, username);
    }

    // ───────────────────── APPROVE ─────────────────────

    public void approve(Long taskId, Long userId, String username,
                        String comment, String idempotencyKey) {
        approvalService.approve(taskId, userId, username, comment, idempotencyKey);
    }

    // ───────────────────── REJECT ─────────────────────

    public void reject(Long taskId, Long userId, String username,
                       String comment, String idempotencyKey) {
        approvalService.reject(taskId, userId, username, comment, idempotencyKey);
    }

    // ───────────────────── WITHDRAW ─────────────────────

    public void withdraw(Long instanceId, Long userId, String username) {
        withdrawService.withdraw(instanceId, userId, username);
    }

    // ───────────────────── TRANSFER ─────────────────────

    public void transfer(Long taskId, Long targetUserId, Long userId,
                         String username, String comment) {
        taskService.transfer(taskId, targetUserId, userId, username, comment);
    }

    // ───────────────────── ADD SIGN ─────────────────────

    public void addSign(Long taskId, List<Long> additionalUserIds, Long userId,
                        String username, String comment) {
        taskService.addSign(taskId, additionalUserIds, userId, username, comment);
    }

    // ───────────────────── QUERY METHODS ─────────────────────

    public List<String> getAvailableActions(Long instanceId, Long userId) {
        return getAvailableActions(null, instanceId, userId);
    }

    public List<String> getAvailableActions(Long tenantId, Long instanceId, Long userId) {
        LambdaQueryWrapper<WfInstance> instanceWrapper = new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getId, instanceId);
        if (tenantId != null) {
            instanceWrapper.eq(WfInstance::getTenantId, tenantId);
        }
        WfInstance instance = wfInstanceMapper.selectOne(instanceWrapper);
        if (instance == null) return List.of();

        List<String> actions = new ArrayList<>();

        if (WorkflowConstants.INSTANCE_RUNNING.equals(instance.getInstanceStatus())) {
            if (instance.getInitiatorId().equals(userId)) {
                actions.add(WorkflowConstants.UI_WITHDRAW);
            }
            // Check if user has pending tasks
            LambdaQueryWrapper<WfTask> pendingWrapper = new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getInstanceId, instanceId)
                    .eq(WfTask::getApproverId, userId)
                    .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING);
            if (tenantId != null) {
                pendingWrapper.eq(WfTask::getTenantId, tenantId);
            }
            long pendingCount = wfTaskMapper.selectCount(pendingWrapper);
            if (pendingCount > 0) {
                actions.add(WorkflowConstants.UI_APPROVE);
                actions.add(WorkflowConstants.UI_REJECT);
                actions.add(WorkflowConstants.UI_TRANSFER);
                actions.add(WorkflowConstants.UI_ADD_SIGN);
            }
        }

        if (WorkflowConstants.INSTANCE_REJECTED.equals(instance.getInstanceStatus())
                && instance.getInitiatorId().equals(userId)) {
            actions.add(WorkflowConstants.UI_RESUBMIT);
        }

        if (WorkflowConstants.INSTANCE_WITHDRAWN.equals(instance.getInstanceStatus())
                && instance.getInitiatorId().equals(userId)) {
            actions.add(WorkflowConstants.UI_RESUBMIT);
        }

        return actions;
    }
}
