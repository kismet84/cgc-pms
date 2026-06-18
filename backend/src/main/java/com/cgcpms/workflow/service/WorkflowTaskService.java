package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles transfer and add-sign workflow operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTaskService {

    private final WorkflowCoreService core;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfNodeInstanceMapper wfNodeInstanceMapper;
    private final WfTaskMapper wfTaskMapper;

    @Transactional
    public void transfer(Long taskId, Long targetUserId, Long userId,
                         String username, String comment) {

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

        // CAS update: atomically mark original task as TRANSFERRED
        int updated = wfTaskMapper.updateTaskStatusWithCas(
                taskId,
                WorkflowConstants.TASK_PENDING,
                task.getTaskVersion(),
                WorkflowConstants.TASK_TRANSFERRED,
                WorkflowConstants.ACTION_TRANSFER,
                "转办给用户 " + targetUserId + ": " + (comment != null ? comment : ""),
                LocalDateTime.now());
        if (updated != 1) {
            throw new BusinessException("TASK_VERSION_CONFLICT", "任务已被他人处理（乐观锁冲突），无法转办");
        }

        // Ping instance to acquire row lock and verify still RUNNING
        int instanceOk = wfInstanceMapper.pingInstanceRunning(task.getInstanceId(),
                WorkflowConstants.INSTANCE_RUNNING);
        if (instanceOk != 1) {
            throw new BusinessException("INSTANCE_STATUS_CONFLICT", "审批实例状态已变更，无法转办");
        }

        // Only create new task after CAS confirms original update succeeded
        WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
        WfTask newTask = new WfTask();
        newTask.setTenantId(instance.getTenantId());
        newTask.setInstanceId(task.getInstanceId());
        newTask.setNodeInstanceId(task.getNodeInstanceId());
        newTask.setBusinessType(task.getBusinessType());
        newTask.setBusinessId(task.getBusinessId());
        newTask.setApproverId(targetUserId);
        SysUser targetUser = core.sysUserMapper.selectById(targetUserId);
        newTask.setApproverName(targetUser != null
                ? (targetUser.getRealName() != null ? targetUser.getRealName() : targetUser.getUsername())
                : "");
        newTask.setTaskStatus(WorkflowConstants.TASK_PENDING);
        newTask.setRoundNo(task.getRoundNo());
        newTask.setReceivedAt(LocalDateTime.now());
        wfTaskMapper.insert(newTask);

        // Notify transferee — use instance tenantId, reload if needed
        try {
            Long notifyTenantId = instance.getTenantId();
            if (notifyTenantId == null || notifyTenantId == 0L) {
                WfInstance reloaded = wfInstanceMapper.selectById(task.getInstanceId());
                notifyTenantId = reloaded != null ? reloaded.getTenantId() : 0L;
            }
            core.notificationService.create(notifyTenantId, targetUserId,
                    username + "转办了一个审批给你",
                    username + "转办了一个审批给你：" + instance.getTitle(),
                    "WORKFLOW", instance.getId());
        } catch (Exception e) {
            log.warn("Failed to create transfer notification for user {}: {}", targetUserId, e.getMessage());
        }

        core.writeRecord(task.getTenantId(), task.getBusinessType(), task.getBusinessId(),
                task.getInstanceId(), task.getNodeInstanceId(), taskId, task.getRoundNo(),
                null, null, WorkflowConstants.ACTION_TRANSFER, "转办",
                userId, username, comment);
    }

    @Transactional
    public void addSign(Long taskId, List<Long> additionalUserIds, Long userId,
                        String username, String comment) {

        WfTask task = wfTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("TASK_NOT_FOUND", "审批任务不存在");
        }
        if (!WorkflowConstants.TASK_PENDING.equals(task.getTaskStatus())) {
            throw new BusinessException("TASK_ALREADY_HANDLED", "该任务已被处理");
        }
        if (!task.getApproverId().equals(userId)) {
            throw new BusinessException("NOT_TASK_OWNER", "非当前任务审批人，无法加签");
        }

        WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw new BusinessException("INSTANCE_NOT_FOUND", "审批实例不存在");
        }
        if (!WorkflowConstants.INSTANCE_RUNNING.equals(instance.getInstanceStatus())) {
            throw new BusinessException("INSTANCE_NOT_RUNNING", "只能对运行中的审批加签");
        }
        WfNodeInstance node = wfNodeInstanceMapper.selectById(task.getNodeInstanceId());
        if (node == null || !WorkflowConstants.NODE_ACTIVE.equals(node.getNodeStatus())) {
            throw new BusinessException("NODE_NOT_ACTIVE", "只能对当前活动节点加签");
        }
        // Batch-fetch user names for all signees
        Map<Long, SysUser> signUserMap = Collections.emptyMap();
        if (!additionalUserIds.isEmpty()) {
            List<SysUser> signUsers = core.sysUserMapper.selectBatchIds(
                    new HashSet<>(additionalUserIds));
            signUserMap = signUsers.stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        }
        for (Long auid : additionalUserIds) {
            // Check not already exists
            long exists = wfTaskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getNodeInstanceId, task.getNodeInstanceId())
                    .eq(WfTask::getApproverId, auid)
                    .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
            if (exists > 0) continue;

            WfTask addTask = new WfTask();
            addTask.setTenantId(instance.getTenantId());
            addTask.setInstanceId(task.getInstanceId());
            addTask.setNodeInstanceId(task.getNodeInstanceId());
            addTask.setBusinessType(task.getBusinessType());
            addTask.setBusinessId(task.getBusinessId());
            addTask.setApproverId(auid);
            SysUser signUser = signUserMap.get(auid);
            addTask.setApproverName(signUser != null
                    ? (signUser.getRealName() != null ? signUser.getRealName() : signUser.getUsername())
                    : "");
            addTask.setTaskStatus(WorkflowConstants.TASK_PENDING);
            addTask.setRoundNo(task.getRoundNo());
            addTask.setReceivedAt(LocalDateTime.now());
            wfTaskMapper.insert(addTask);

            // Notify signee — re-query instance to ensure fresh tenantId
            try {
                WfInstance instanceForNotify = wfInstanceMapper.selectById(task.getInstanceId());
                Long notifyTenantId = instanceForNotify != null ? instanceForNotify.getTenantId() : instance.getTenantId();
                String notifyTitle = instanceForNotify != null ? instanceForNotify.getTitle() : instance.getTitle();
                core.notificationService.create(notifyTenantId, auid,
                        username + "邀请你加签审批",
                        username + "邀请你加签审批：" + notifyTitle,
                        "WORKFLOW", task.getInstanceId());
            } catch (Exception e) {
                log.warn("Failed to create add-sign notification for user {}: {}", auid, e.getMessage());
            }
        }

        core.writeRecord(task.getTenantId(), task.getBusinessType(), task.getBusinessId(),
                task.getInstanceId(), task.getNodeInstanceId(), taskId, task.getRoundNo(),
                null, null, WorkflowConstants.ACTION_ADD_SIGN, "加签",
                userId, username, comment);
    }
}
