package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.notification.service.NotificationService;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowBusinessHandlerRegistry;
import com.cgcpms.workflow.handler.WorkflowContext;
import com.cgcpms.workflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Internal shared helpers for workflow sub-services.
 * Package-private — not for external consumption.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class WorkflowCoreService {

    final WfTemplateMapper wfTemplateMapper;
    final WfTemplateNodeMapper wfTemplateNodeMapper;
    final WfInstanceMapper wfInstanceMapper;
    final WfNodeInstanceMapper wfNodeInstanceMapper;
    final WfTaskMapper wfTaskMapper;
    final WfRecordMapper wfRecordMapper;
    final WfIdempotencyMapper wfIdempotencyMapper;
    final SysUserMapper sysUserMapper;
    final WorkflowBusinessHandlerRegistry handlerRegistry;
    final NotificationService notificationService;
    final ApproverResolver approverResolver;

    // ── Template lookup ──

    WfTemplate findTemplate(String businessType, Long tenantId, java.math.BigDecimal amount) {
        // First try the specific tenant, then fall back to tenant 0 (default templates)
        WfTemplate template = queryTemplate(businessType, tenantId, amount);
        if (template == null && tenantId != null && tenantId != 0L) {
            template = queryTemplate(businessType, 0L, amount);
        }
        if (template == null) {
            throw new BusinessException("TEMPLATE_NOT_FOUND",
                    "未找到业务类型 [" + businessType + "] 的审批模板");
        }
        return template;
    }

    private WfTemplate queryTemplate(String businessType, Long tenantId, java.math.BigDecimal amount) {
        LambdaQueryWrapper<WfTemplate> wrapper = new LambdaQueryWrapper<WfTemplate>()
                .eq(WfTemplate::getBusinessType, businessType)
                .eq(WfTemplate::getTenantId, tenantId)
                .eq(WfTemplate::getEnabled, 1);
        // Filter by amount thresholds when amount is provided
        if (amount != null) {
            wrapper.and(w -> w.isNull(WfTemplate::getAmountMin)
                    .or().le(WfTemplate::getAmountMin, amount))
                    .and(w -> w.isNull(WfTemplate::getAmountMax)
                            .or().ge(WfTemplate::getAmountMax, amount));
        }
        List<WfTemplate> templates = wfTemplateMapper.selectList(wrapper);
        if (templates.isEmpty()) return null;
        // Deterministic ordering: prefer most specific interval (smallest amountMin first,
        // then largest amountMax first so narrower intervals beat wider ones).
        // When amounts are identical, prefer the earliest-created template.
        templates.sort(java.util.Comparator
                .comparing(WfTemplate::getAmountMin, java.util.Comparator.nullsFirst(java.math.BigDecimal::compareTo))
                .thenComparing(WfTemplate::getAmountMax, java.util.Comparator.nullsLast(java.math.BigDecimal::compareTo).reversed())
                .thenComparing(WfTemplate::getCreatedAt, java.util.Comparator.nullsLast(LocalDateTime::compareTo)));
        return templates.get(0);
    }

    List<WfTemplateNode> findTemplateNodes(Long templateId) {
        List<WfTemplateNode> nodes = wfTemplateNodeMapper.selectList(
                new LambdaQueryWrapper<WfTemplateNode>()
                        .eq(WfTemplateNode::getTemplateId, templateId)
                        .orderByAsc(WfTemplateNode::getNodeOrder));
        if (nodes.isEmpty()) {
            throw new BusinessException("TEMPLATE_NODES_EMPTY", "审批模板未配置节点");
        }
        return nodes;
    }

    // ── Node activation ──

    void activateNode(WfNodeInstance node, WfTemplateNode tplNode,
                      Long userId, String username, Long tenantId) {
        node.setNodeStatus(WorkflowConstants.NODE_ACTIVE);
        node.setStartedAt(LocalDateTime.now());
        wfNodeInstanceMapper.updateById(node);
        createTasksForNode(node, tplNode, userId, username, tenantId);
    }

    void reactivateNode(WfNodeInstance node, WfTemplateNode tplNode,
                        Long userId, String username, Long tenantId, int roundNo) {
        node.setNodeStatus(WorkflowConstants.NODE_ACTIVE);
        node.setRoundNo(roundNo);
        node.setStartedAt(LocalDateTime.now());
        node.setEndedAt(null);
        wfNodeInstanceMapper.updateById(node);
        createTasksForNode(node, tplNode, userId, username, tenantId);
    }

    void createTasksForNode(WfNodeInstance node, WfTemplateNode tplNode,
                            Long userId, String username, Long tenantId) {
        WfInstance instance = wfInstanceMapper.selectById(node.getInstanceId());

        // Resolve approvers from template node approverConfig JSON
        List<Long> approverIds = approverResolver.resolve(
                tplNode.getApproverConfig(), tenantId, instance.getProjectId());

        for (Long approverId : approverIds) {
            SysUser approverUser = sysUserMapper.selectById(approverId);
            String approverName = approverUser != null
                    ? (approverUser.getRealName() != null ? approverUser.getRealName() : approverUser.getUsername())
                    : "";

            WfTask task = new WfTask();
            task.setTenantId(tenantId);
            task.setInstanceId(node.getInstanceId());
            task.setNodeInstanceId(node.getId());
            task.setBusinessType(instance.getBusinessType());
            task.setBusinessId(instance.getBusinessId());
            task.setApproverId(approverId);
            task.setApproverName(approverName);
            task.setTaskStatus(WorkflowConstants.TASK_PENDING);
            task.setRoundNo(node.getRoundNo());
            task.setReceivedAt(LocalDateTime.now());
            wfTaskMapper.insert(task);
        }
    }

    // ── Node completion ──

    boolean isNodeComplete(Long nodeInstanceId, String approveMode) {
        long pendingCount = wfTaskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getNodeInstanceId, nodeInstanceId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));

        if (WorkflowConstants.MODE_OR_SIGN.equals(approveMode)) {
            // OR_SIGN: node is complete if any task is approved (pending count < total is irrelevant)
            // Actually, if node is still active, and someone approved, it means the first approval triggered node completion
            // So here, if we arrived at this check, someone approved, which is enough for OR_SIGN
            cancelPendingTasksInNode(nodeInstanceId, null);
            return true;
        }

        // SEQUENTIAL or COUNTERSIGN: all tasks must be completed
        return pendingCount == 0;
    }

    void completeNode(Long nodeInstanceId) {
        WfNodeInstance node = wfNodeInstanceMapper.selectById(nodeInstanceId);
        if (node != null) {
            node.setNodeStatus(WorkflowConstants.NODE_COMPLETED);
            node.setEndedAt(LocalDateTime.now());
            wfNodeInstanceMapper.updateById(node);
        }
    }

    // ── Node traversal ──

    WfNodeInstance findNextWaitingNode(Long instanceId, int roundNo) {
        List<WfNodeInstance> nodes = wfNodeInstanceMapper.selectList(
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, instanceId)
                        .eq(WfNodeInstance::getRoundNo, roundNo)
                        .eq(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_WAITING)
                        .orderByAsc(WfNodeInstance::getNodeOrder)
                        .last("LIMIT 1"));
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    WfNodeInstance findRejectedOrLastNode(Long instanceId) {
        // Find the rejected node (highest order)
        List<WfNodeInstance> nodes = wfNodeInstanceMapper.selectList(
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, instanceId)
                        .and(w -> w.eq(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_REJECTED)
                                .or().eq(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_ACTIVE))
                        .orderByDesc(WfNodeInstance::getNodeOrder)
                        .last("LIMIT 1"));
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    // ── Task cancellation ──

    void cancelPendingTasksInNode(Long nodeInstanceId, Long excludeTaskId) {
        // Direct SQL update: only cancel tasks that are still PENDING at the moment of update.
        // This avoids the select-then-update TOCTOU race with concurrent approve/transfer.
        LambdaUpdateWrapper<WfTask> wrapper = new LambdaUpdateWrapper<WfTask>()
                .eq(WfTask::getNodeInstanceId, nodeInstanceId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING);
        if (excludeTaskId != null) {
            wrapper.ne(WfTask::getId, excludeTaskId);
        }
        wfTaskMapper.update(null, wrapper
                .set(WfTask::getTaskStatus, WorkflowConstants.TASK_CANCELLED)
                .set(WfTask::getHandledAt, LocalDateTime.now())
                .setSql("task_version = task_version + 1"));
    }

    void cancelAllPendingTasks(Long instanceId) {
        // Direct SQL update: only cancel tasks that are still PENDING at the moment of update.
        // This avoids the select-then-update TOCTOU race with concurrent approve/transfer.
        wfTaskMapper.update(null, new LambdaUpdateWrapper<WfTask>()
                .eq(WfTask::getInstanceId, instanceId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                .set(WfTask::getTaskStatus, WorkflowConstants.TASK_CANCELLED)
                .set(WfTask::getHandledAt, LocalDateTime.now())
                .setSql("task_version = task_version + 1"));
    }

    void resetActiveNodes(Long instanceId) {
        List<WfNodeInstance> nodes = wfNodeInstanceMapper.selectList(
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, instanceId)
                        .eq(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_ACTIVE));
        // M-010: Batch update instead of loop
        if (!nodes.isEmpty()) {
            List<Long> nodeIds = nodes.stream().map(WfNodeInstance::getId).collect(Collectors.toList());
            wfNodeInstanceMapper.update(null, new LambdaUpdateWrapper<WfNodeInstance>()
                    .in(WfNodeInstance::getId, nodeIds)
                    .set(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_WAITING)
                    .set(WfNodeInstance::getEndedAt, LocalDateTime.now()));
        }
    }

    // ── Record writing ──

    void writeRecord(Long tenantIdOverride, String businessTypeOverride, Long businessIdOverride,
                     Long instanceId, Long nodeInstanceId, Long taskId,
                     int roundNo, String nodeCode, String nodeName,
                     String actionType, String actionName,
                     Long operatorId, String operatorName, String comment) {
        WfRecord record = new WfRecord();
        Long tenantId = tenantIdOverride != null ? tenantIdOverride : 0L;
        record.setInstanceId(instanceId);
        record.setNodeInstanceId(nodeInstanceId);
        record.setTaskId(taskId);
        record.setRoundNo(roundNo);
        String businessType = businessTypeOverride;
        Long businessId = businessIdOverride;
        try {
            WfInstance inst = wfInstanceMapper.selectById(instanceId);
            if (inst != null) {
                if (tenantIdOverride == null) {
                    tenantId = inst.getTenantId();
                }
                if (businessType == null) {
                    businessType = inst.getBusinessType();
                }
                if (businessId == null) {
                    businessId = inst.getBusinessId();
                }
            }
        } catch (Exception ignored) {
            log.error("Failed to save workflow record", ignored);
        }
        record.setTenantId(tenantId);
        record.setBusinessType(businessType);
        record.setBusinessId(businessId);
        record.setNodeCode(nodeCode);
        record.setNodeName(nodeName);
        record.setActionType(actionType);
        record.setActionName(actionName);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setComment(comment);
        record.setRecordStatus(WorkflowConstants.RECORD_EFFECTIVE);
        wfRecordMapper.insert(record);
    }

    // ── Idempotency ──

    void checkIdempotency(Long tenantId, Long userId, String idempotencyKey, String actionType) {
        // Insert-first strategy: rely on the unique constraint
        // uk_wf_idempotency(tenant_id, user_id, idempotency_key) to atomically
        // detect duplicates and avoid the check-then-insert (TOCTOU) race.
        WfIdempotency idem = new WfIdempotency();
        idem.setTenantId(tenantId != null ? tenantId : 0L);
        idem.setUserId(userId);
        idem.setIdempotencyKey(idempotencyKey);
        idem.setCreatedAt(LocalDateTime.now());
        idem.setExpiredAt(LocalDateTime.now().plusHours(WorkflowConstants.IDEMPOTENCY_EXPIRE_HOURS));
        try {
            wfIdempotencyMapper.insert(idem);
        } catch (DuplicateKeyException e) {
            // Another concurrent request with the same key already inserted the record.
            throw new BusinessException("DUPLICATE_REQUEST", "重复请求，操作已执行过，请勿重复提交");
        }
    }

    // ── Handler notification ──

    void notifyHandler(String businessType, WfInstance instance,
                       String actionType, String operatorName, String comment) {
        if (!handlerRegistry.hasHandler(businessType)) {
            log.debug("No business handler registered for type: {}", businessType);
            return;
        }
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);
        ctx.setActionType(actionType);
        ctx.setOperatorName(operatorName);
        ctx.setComment(comment);
        var handler = handlerRegistry.get(businessType);

        if (handler.isCritical()) {
            // Critical handler: let exceptions propagate to trigger @Transactional rollback.
            dispatchToHandler(handler, ctx, instance, actionType);
        } else {
            // Non-critical handler: swallow and log to avoid breaking the approval flow.
            try {
                dispatchToHandler(handler, ctx, instance, actionType);
            } catch (Exception e) {
                log.error("Business handler error for type={}, action={}", businessType, actionType, e);
            }
        }
    }

    private void dispatchToHandler(WorkflowBusinessHandler handler, WorkflowContext ctx,
                                   WfInstance instance, String actionType) {
        switch (actionType) {
            case WorkflowConstants.ACTION_SUBMIT -> handler.beforeSubmit(ctx);
            case WorkflowConstants.ACTION_APPROVE -> {
                if (WorkflowConstants.INSTANCE_APPROVED.equals(instance.getInstanceStatus())) {
                    handler.onApproved(ctx);
                } else {
                    handler.onRunning(ctx);
                }
            }
            case WorkflowConstants.ACTION_REJECT -> handler.onRejected(ctx);
            case WorkflowConstants.ACTION_WITHDRAW -> handler.onWithdrawn(ctx);
        }
    }
}
