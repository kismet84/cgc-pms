package com.cgcpms.workflow.service;

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
import java.util.Objects;
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

    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long taskId, Long targetUserId, Long userId,
                         String username, String comment) {
        TaskActionRoute route = lockTaskActionRoute(taskId, userId, true);
        WfTask task = route.task();
        WfInstance instance = route.instance();
        WfNodeInstance node = route.node();

        // Validate target user belongs to the same tenant as the instance
        SysUser targetUser = core.sysUserMapper.selectById(targetUserId);
        if (targetUser == null || !Objects.equals(targetUser.getTenantId(), instance.getTenantId())) {
            throw new BusinessException("WORKFLOW_TARGET_USER_INVALID", "目标用户不属于当前租户");
        }
        core.requireEligibleApprover(node, instance, targetUserId);

        // CAS update: atomically mark original task as TRANSFERRED
        int updated = wfTaskMapper.updateTaskStatusWithCas(
                taskId,
                WorkflowConstants.TASK_PENDING,
                task.getTaskVersion(),
                WorkflowConstants.TASK_TRANSFERRED,
                WorkflowConstants.ACTION_TRANSFER,
                "转办给用户 " + targetUserId + ": " + (comment != null ? comment : ""),
                LocalDateTime.now(),
                task.getTenantId());
        if (updated != 1) {
            throw new BusinessException("TASK_VERSION_CONFLICT", "任务已被他人处理（乐观锁冲突），无法转办");
        }

        // Only create new task after CAS confirms original update succeeded
        WfTask newTask = new WfTask();
        newTask.setTenantId(instance.getTenantId());
        newTask.setInstanceId(task.getInstanceId());
        newTask.setNodeInstanceId(task.getNodeInstanceId());
        newTask.setBusinessType(task.getBusinessType());
        newTask.setBusinessId(task.getBusinessId());
        newTask.setApproverId(targetUserId);
        targetUser = core.sysUserMapper.selectById(targetUserId);
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

    @Transactional(rollbackFor = Exception.class)
    public void addSign(Long taskId, List<Long> additionalUserIds, Long userId,
                        String username, String comment) {
        TaskActionRoute route = lockTaskActionRoute(taskId, userId, false);
        WfTask task = route.task();
        WfInstance instance = route.instance();
        WfNodeInstance node = route.node();
        // Batch-fetch user names for all signees and validate tenant membership
        Map<Long, SysUser> signUserMap = Collections.emptyMap();
        if (!additionalUserIds.isEmpty()) {
            List<SysUser> signUsers = core.sysUserMapper.selectByIds(
                    new HashSet<>(additionalUserIds));
            signUserMap = signUsers.stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
            // Validate every additional user belongs to the same tenant as the instance
            for (Long additionalUserId : additionalUserIds) {
                SysUser signUser = signUserMap.get(additionalUserId);
                if (signUser == null || !Objects.equals(signUser.getTenantId(), instance.getTenantId())) {
                    throw new BusinessException("WORKFLOW_TARGET_USER_INVALID", "加签用户不属于当前租户");
                }
                core.requireEligibleApprover(node, instance, additionalUserId);
            }
        }
        boolean taskAdded = false;
        for (Long auid : additionalUserIds) {
            // Check not already exists
            if (!wfTaskMapper.selectPendingApproverIdsForUpdate(
                    task.getTenantId(), task.getNodeInstanceId(), auid).isEmpty()) continue;

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
            taskAdded = true;

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

        if (taskAdded) {
            core.writeRecord(task.getTenantId(), task.getBusinessType(), task.getBusinessId(),
                    task.getInstanceId(), task.getNodeInstanceId(), taskId, task.getRoundNo(),
                    null, null, WorkflowConstants.ACTION_ADD_SIGN, "加签",
                    userId, username, comment);
        }
    }

    private TaskActionRoute lockTaskActionRoute(Long taskId, Long userId, boolean transfer) {
        WfTask probe = wfTaskMapper.selectByIdIgnoringTenant(taskId);
        if (probe == null) throw new BusinessException("TASK_NOT_FOUND", "审批任务不存在");
        core.requireCurrentTenant(probe.getTenantId());

        WfInstance instance = wfInstanceMapper.selectByIdForUpdate(probe.getInstanceId(), probe.getTenantId());
        if (instance == null) throw new BusinessException("INSTANCE_NOT_FOUND", "审批实例不存在");
        if (!WorkflowConstants.INSTANCE_RUNNING.equals(instance.getInstanceStatus())) {
            throw new BusinessException("INSTANCE_STATUS_CONFLICT", "审批实例状态已变更，无法处理任务");
        }
        WfNodeInstance node = wfNodeInstanceMapper.selectByIdForUpdate(probe.getNodeInstanceId(), probe.getTenantId());
        if (node == null) throw new BusinessException("WORKFLOW_NODE_NOT_FOUND", "审批实例节点不存在");
        if (!WorkflowConstants.NODE_ACTIVE.equals(node.getNodeStatus())) {
            throw new BusinessException("NODE_NOT_ACTIVE", "只能操作当前活动审批节点");
        }
        boolean allowed = transfer
                ? Integer.valueOf(1).equals(node.getAllowTransfer())
                : Integer.valueOf(1).equals(node.getAllowAddSign());
        if (!allowed) {
            throw new BusinessException(
                    transfer ? "WORKFLOW_TRANSFER_NOT_ALLOWED" : "WORKFLOW_ADD_SIGN_NOT_ALLOWED",
                    transfer ? "当前审批节点不允许转办" : "当前审批节点不允许加签");
        }
        WfTask task = wfTaskMapper.selectByIdForUpdate(taskId, probe.getTenantId());
        if (task == null) throw new BusinessException("TASK_NOT_FOUND", "审批任务不存在");
        if (!WorkflowConstants.TASK_PENDING.equals(task.getTaskStatus())) {
            throw new BusinessException("TASK_ALREADY_HANDLED", "该任务已被处理");
        }
        if (!task.getApproverId().equals(userId)) {
            throw new BusinessException("NOT_TASK_OWNER", transfer ? "非当前任务审批人" : "非当前任务审批人，无法加签");
        }
        return new TaskActionRoute(task, instance, node);
    }

    private record TaskActionRoute(WfTask task, WfInstance instance, WfNodeInstance node) {}
}
