package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.handler.WorkflowBusinessHandlerRegistry;
import com.cgcpms.workflow.handler.WorkflowContext;
import com.cgcpms.workflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Core workflow engine handling submit, approve, reject, withdraw,
 * resubmit, transfer, and add-sign operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final WfTemplateMapper wfTemplateMapper;
    private final WfTemplateNodeMapper wfTemplateNodeMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfNodeInstanceMapper wfNodeInstanceMapper;
    private final WfTaskMapper wfTaskMapper;
    private final WfRecordMapper wfRecordMapper;
    private final WfIdempotencyMapper wfIdempotencyMapper;
    private final WorkflowBusinessHandlerRegistry handlerRegistry;

    // ───────────────────── SUBMIT ─────────────────────

    @Transactional
    public WfInstance submit(Long userId, String username, Long tenantId,
                             String businessType, Long businessId,
                             String title, java.math.BigDecimal amount,
                             Long projectId, Long contractId,
                             String businessSummary, String variables) {

        WfTemplate template = findTemplate(businessType);
        List<WfTemplateNode> templateNodes = findTemplateNodes(template.getId());

        // Create instance
        WfInstance instance = new WfInstance();
        instance.setTenantId(tenantId);
        instance.setTemplateId(template.getId());
        instance.setBusinessType(businessType);
        instance.setBusinessId(businessId);
        instance.setProjectId(projectId);
        instance.setContractId(contractId);
        instance.setTitle(title);
        instance.setAmount(amount);
        instance.setInstanceStatus(WorkflowConstants.INSTANCE_RUNNING);
        instance.setCurrentRound(1);
        instance.setResubmitCount(0);
        instance.setBusinessRevision(1);
        instance.setInitiatorId(userId);
        instance.setBusinessSummary(businessSummary);
        instance.setVariables(variables);
        instance.setStartedAt(LocalDateTime.now());
        wfInstanceMapper.insert(instance);

        // Create node instances
        List<WfNodeInstance> nodeInstances = new ArrayList<>();
        for (WfTemplateNode tn : templateNodes) {
            WfNodeInstance ni = new WfNodeInstance();
            ni.setTenantId(tenantId);
            ni.setInstanceId(instance.getId());
            ni.setTemplateNodeId(tn.getId());
            ni.setNodeCode(tn.getNodeCode());
            ni.setNodeName(tn.getNodeName());
            ni.setNodeOrder(tn.getNodeOrder());
            ni.setApproveMode(tn.getApproveMode());
            ni.setNodeStatus(WorkflowConstants.NODE_WAITING);
            ni.setRoundNo(1);
            ni.setPassRuleJson(tn.getPassRuleJson());
            ni.setRejectRuleJson(tn.getRejectRuleJson());
            wfNodeInstanceMapper.insert(ni);
            nodeInstances.add(ni);
        }

        // Activate first node and create tasks
        if (!nodeInstances.isEmpty()) {
            WfNodeInstance firstNode = nodeInstances.get(0);
            activateNode(firstNode, templateNodes.get(0), userId, username, tenantId);
        }

        // Write submit record
        writeRecord(instance.getId(), null, null, 1, null, null,
                WorkflowConstants.ACTION_SUBMIT, "提交审批",
                userId, username, null);

        // Notify business handler
        notifyHandler(businessType, instance, WorkflowConstants.ACTION_SUBMIT, username, null);

        return instance;
    }

    // ───────────────────── APPROVE ─────────────────────

    @Transactional
    public void approve(Long taskId, Long userId, String username,
                        String comment, String idempotencyKey) {

        checkIdempotency(userId, idempotencyKey, WorkflowConstants.ACTION_APPROVE);

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
        writeRecord(task.getInstanceId(), task.getNodeInstanceId(), taskId, task.getRoundNo(),
                nodeInstance != null ? nodeInstance.getNodeCode() : null,
                nodeInstance != null ? nodeInstance.getNodeName() : null,
                WorkflowConstants.ACTION_APPROVE, "同意",
                userId, username, comment);

        // Check if node is complete
        String approveMode = nodeInstance != null ? nodeInstance.getApproveMode() : WorkflowConstants.MODE_SEQUENTIAL;
        if (isNodeComplete(task.getNodeInstanceId(), approveMode)) {
            // Mark node completed
            completeNode(task.getNodeInstanceId());

            // Find next waiting node
            WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
            WfNodeInstance nextNode = findNextWaitingNode(instance.getId(), instance.getCurrentRound());

            if (nextNode != null) {
                // Activate next node
                WfTemplateNode nextTplNode = wfTemplateNodeMapper.selectById(nextNode.getTemplateNodeId());
                activateNode(nextNode, nextTplNode, userId, username,
                        instance.getTenantId());
            } else {
                // All nodes complete → instance approved
                instance.setInstanceStatus(WorkflowConstants.INSTANCE_APPROVED);
                instance.setEndedAt(LocalDateTime.now());
                wfInstanceMapper.updateById(instance);

                writeRecord(instance.getId(), null, null, instance.getCurrentRound(),
                        null, null, WorkflowConstants.ACTION_APPROVE, "审批通过",
                        userId, username, "所有节点审批通过");

                notifyHandler(instance.getBusinessType(), instance,
                        WorkflowConstants.ACTION_APPROVE, username, null);
            }
        }
    }

    // ───────────────────── REJECT ─────────────────────

    @Transactional
    public void reject(Long taskId, Long userId, String username,
                       String comment, String idempotencyKey) {

        checkIdempotency(userId, idempotencyKey, WorkflowConstants.ACTION_REJECT);

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

        task.setTaskStatus(WorkflowConstants.TASK_REJECTED);
        task.setActionType(WorkflowConstants.ACTION_REJECT);
        task.setComment(comment);
        task.setHandledAt(LocalDateTime.now());
        int updated = wfTaskMapper.updateById(task);
        if (updated == 0) {
            throw new BusinessException("TASK_VERSION_CONFLICT", "任务已被他人处理（乐观锁冲突）");
        }

        // Cancel other pending tasks in the same node
        cancelPendingTasksInNode(task.getNodeInstanceId(), taskId);

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

        writeRecord(task.getInstanceId(), task.getNodeInstanceId(), taskId, task.getRoundNo(),
                nodeInstance != null ? nodeInstance.getNodeCode() : null,
                nodeInstance != null ? nodeInstance.getNodeName() : null,
                WorkflowConstants.ACTION_REJECT, "驳回",
                userId, username, comment);

        notifyHandler(instance.getBusinessType(), instance,
                WorkflowConstants.ACTION_REJECT, username, comment);
    }

    // ───────────────────── WITHDRAW ─────────────────────

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

        cancelAllPendingTasks(instanceId);
        resetActiveNodes(instanceId);

        instance.setInstanceStatus(WorkflowConstants.INSTANCE_WITHDRAWN);
        instance.setEndedAt(LocalDateTime.now());
        wfInstanceMapper.updateById(instance);

        writeRecord(instanceId, null, null, instance.getCurrentRound(),
                null, null, WorkflowConstants.ACTION_WITHDRAW, "撤回",
                userId, username, null);

        notifyHandler(instance.getBusinessType(), instance,
                WorkflowConstants.ACTION_WITHDRAW, username, null);
    }

    // ───────────────────── RESUBMIT ─────────────────────

    @Transactional
    public WfInstance resubmit(Long instanceId, Long userId, String username) {

        WfInstance instance = wfInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException("INSTANCE_NOT_FOUND", "审批实例不存在");
        }
        if (!WorkflowConstants.INSTANCE_REJECTED.equals(instance.getInstanceStatus())
                && !WorkflowConstants.INSTANCE_WITHDRAWN.equals(instance.getInstanceStatus())) {
            throw new BusinessException("INSTANCE_NOT_RESUBMITTABLE", "只能重新提交已驳回或已撤回的审批");
        }
        if (!instance.getInitiatorId().equals(userId)) {
            throw new BusinessException("NOT_INITIATOR", "只有发起人可以重新提交");
        }

        int newRound = instance.getCurrentRound() + 1;
        instance.setCurrentRound(newRound);
        instance.setResubmitCount(instance.getResubmitCount() + 1);
        instance.setInstanceStatus(WorkflowConstants.INSTANCE_RUNNING);
        instance.setEndedAt(null);
        wfInstanceMapper.updateById(instance);

        // Reactivate the previously rejected node
        WfNodeInstance rejectedNode = findRejectedOrLastNode(instanceId);
        if (rejectedNode != null) {
            WfTemplateNode tplNode = wfTemplateNodeMapper.selectById(rejectedNode.getTemplateNodeId());
            reactivateNode(rejectedNode, tplNode, userId, username, instance.getTenantId(), newRound);
        }

        writeRecord(instanceId, null, null, newRound,
                null, null, WorkflowConstants.ACTION_RESUBMIT, "重新提交",
                userId, username, null);

        return instance;
    }

    // ───────────────────── TRANSFER ─────────────────────

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

        // Mark original task as transferred
        task.setTaskStatus(WorkflowConstants.TASK_TRANSFERRED);
        task.setActionType(WorkflowConstants.ACTION_TRANSFER);
        task.setComment("转办给用户 " + targetUserId + ": " + (comment != null ? comment : ""));
        task.setHandledAt(LocalDateTime.now());
        wfTaskMapper.updateById(task);

        // Create new task for target
        WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
        WfTask newTask = new WfTask();
        newTask.setTenantId(instance.getTenantId());
        newTask.setInstanceId(task.getInstanceId());
        newTask.setNodeInstanceId(task.getNodeInstanceId());
        newTask.setBusinessType(task.getBusinessType());
        newTask.setBusinessId(task.getBusinessId());
        newTask.setApproverId(targetUserId);
        newTask.setApproverName("U" + targetUserId);
        newTask.setTaskStatus(WorkflowConstants.TASK_PENDING);
        newTask.setRoundNo(task.getRoundNo());
        newTask.setReceivedAt(LocalDateTime.now());
        wfTaskMapper.insert(newTask);

        writeRecord(task.getInstanceId(), task.getNodeInstanceId(), taskId, task.getRoundNo(),
                null, null, WorkflowConstants.ACTION_TRANSFER, "转办",
                userId, username, comment);
    }

    // ───────────────────── ADD SIGN ─────────────────────

    @Transactional
    public void addSign(Long taskId, List<Long> additionalUserIds, Long userId,
                        String username, String comment) {

        WfTask task = wfTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("TASK_NOT_FOUND", "审批任务不存在");
        }
        if (!task.getApproverId().equals(userId)) {
            throw new BusinessException("NOT_TASK_OWNER", "非当前任务审批人，无法加签");
        }

        WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
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
            addTask.setApproverName("U" + auid);
            addTask.setTaskStatus(WorkflowConstants.TASK_PENDING);
            addTask.setRoundNo(task.getRoundNo());
            addTask.setReceivedAt(LocalDateTime.now());
            wfTaskMapper.insert(addTask);
        }

        writeRecord(task.getInstanceId(), task.getNodeInstanceId(), taskId, task.getRoundNo(),
                null, null, WorkflowConstants.ACTION_ADD_SIGN, "加签",
                userId, username, comment);
    }

    // ───────────────────── HELPER METHODS ─────────────────────

    private WfTemplate findTemplate(String businessType) {
        List<WfTemplate> templates = wfTemplateMapper.selectList(
                new LambdaQueryWrapper<WfTemplate>()
                        .eq(WfTemplate::getBusinessType, businessType)
                        .eq(WfTemplate::getEnabled, 1));
        if (templates.isEmpty()) {
            throw new BusinessException("TEMPLATE_NOT_FOUND",
                    "未找到业务类型 [" + businessType + "] 的审批模板");
        }
        return templates.get(0);
    }

    private List<WfTemplateNode> findTemplateNodes(Long templateId) {
        List<WfTemplateNode> nodes = wfTemplateNodeMapper.selectList(
                new LambdaQueryWrapper<WfTemplateNode>()
                        .eq(WfTemplateNode::getTemplateId, templateId)
                        .orderByAsc(WfTemplateNode::getNodeOrder));
        if (nodes.isEmpty()) {
            throw new BusinessException("TEMPLATE_NODES_EMPTY", "审批模板未配置节点");
        }
        return nodes;
    }

    private void activateNode(WfNodeInstance node, WfTemplateNode tplNode,
                              Long userId, String username, Long tenantId) {
        node.setNodeStatus(WorkflowConstants.NODE_ACTIVE);
        node.setStartedAt(LocalDateTime.now());
        wfNodeInstanceMapper.updateById(node);
        createTasksForNode(node, tplNode, userId, username, tenantId);
    }

    private void reactivateNode(WfNodeInstance node, WfTemplateNode tplNode,
                                Long userId, String username, Long tenantId, int roundNo) {
        node.setNodeStatus(WorkflowConstants.NODE_ACTIVE);
        node.setRoundNo(roundNo);
        node.setStartedAt(LocalDateTime.now());
        node.setEndedAt(null);
        wfNodeInstanceMapper.updateById(node);
        createTasksForNode(node, tplNode, userId, username, tenantId);
    }

    private void createTasksForNode(WfNodeInstance node, WfTemplateNode tplNode,
                                    Long userId, String username, Long tenantId) {
        WfInstance instance = wfInstanceMapper.selectById(node.getInstanceId());

        // Parse approver config: for POC, use the demo data approach - single approver
        // In production, approverConfig JSON would be parsed to resolve actual users
        // For POC, create a task for the current user (initiator's manager or self)
        WfTask task = new WfTask();
        task.setTenantId(tenantId);
        task.setInstanceId(node.getInstanceId());
        task.setNodeInstanceId(node.getId());
        task.setBusinessType(instance.getBusinessType());
        task.setBusinessId(instance.getBusinessId());
        task.setApproverId(userId);
        task.setApproverName(username);
        task.setTaskStatus(WorkflowConstants.TASK_PENDING);
        task.setRoundNo(node.getRoundNo());
        task.setReceivedAt(LocalDateTime.now());
        wfTaskMapper.insert(task);
    }

    private boolean isNodeComplete(Long nodeInstanceId, String approveMode) {
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

    private void completeNode(Long nodeInstanceId) {
        WfNodeInstance node = wfNodeInstanceMapper.selectById(nodeInstanceId);
        if (node != null) {
            node.setNodeStatus(WorkflowConstants.NODE_COMPLETED);
            node.setEndedAt(LocalDateTime.now());
            wfNodeInstanceMapper.updateById(node);
        }
    }

    private WfNodeInstance findNextWaitingNode(Long instanceId, int roundNo) {
        List<WfNodeInstance> nodes = wfNodeInstanceMapper.selectList(
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, instanceId)
                        .eq(WfNodeInstance::getRoundNo, roundNo)
                        .eq(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_WAITING)
                        .orderByAsc(WfNodeInstance::getNodeOrder)
                        .last("LIMIT 1"));
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    private WfNodeInstance findRejectedOrLastNode(Long instanceId) {
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

    private void cancelPendingTasksInNode(Long nodeInstanceId, Long excludeTaskId) {
        LambdaQueryWrapper<WfTask> wrapper = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getNodeInstanceId, nodeInstanceId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING);
        if (excludeTaskId != null) {
            wrapper.ne(WfTask::getId, excludeTaskId);
        }
        List<WfTask> tasks = wfTaskMapper.selectList(wrapper);
        for (WfTask t : tasks) {
            t.setTaskStatus(WorkflowConstants.TASK_CANCELLED);
            t.setHandledAt(LocalDateTime.now());
            wfTaskMapper.updateById(t);
        }
    }

    private void cancelAllPendingTasks(Long instanceId) {
        List<WfTask> tasks = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instanceId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        for (WfTask t : tasks) {
            t.setTaskStatus(WorkflowConstants.TASK_CANCELLED);
            t.setHandledAt(LocalDateTime.now());
            wfTaskMapper.updateById(t);
        }
    }

    private void resetActiveNodes(Long instanceId) {
        List<WfNodeInstance> nodes = wfNodeInstanceMapper.selectList(
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, instanceId)
                        .eq(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_ACTIVE));
        for (WfNodeInstance n : nodes) {
            n.setNodeStatus(WorkflowConstants.NODE_WAITING);
            n.setEndedAt(LocalDateTime.now());
            wfNodeInstanceMapper.updateById(n);
        }
    }

    private void writeRecord(Long instanceId, Long nodeInstanceId, Long taskId,
                             int roundNo, String nodeCode, String nodeName,
                             String actionType, String actionName,
                             Long operatorId, String operatorName, String comment) {
        WfRecord record = new WfRecord();
        record.setTenantId(0L);
        record.setInstanceId(instanceId);
        record.setNodeInstanceId(nodeInstanceId);
        record.setTaskId(taskId);
        record.setRoundNo(roundNo);
        String businessType = null;
        Long businessId = null;
        try {
            WfInstance inst = wfInstanceMapper.selectById(instanceId);
            if (inst != null) {
                businessType = inst.getBusinessType();
                businessId = inst.getBusinessId();
            }
        } catch (Exception ignored) {
        }
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

    private void checkIdempotency(Long userId, String idempotencyKey, String actionType) {
        long count = wfIdempotencyMapper.selectCount(new LambdaQueryWrapper<WfIdempotency>()
                .eq(WfIdempotency::getUserId, userId)
                .eq(WfIdempotency::getIdempotencyKey, idempotencyKey));
        if (count > 0) {
            throw new BusinessException("DUPLICATE_REQUEST", "重复请求，操作已执行过，请勿重复提交");
        }
        // Insert idempotency record
        WfIdempotency idem = new WfIdempotency();
        idem.setTenantId(0L);
        idem.setUserId(userId);
        idem.setIdempotencyKey(idempotencyKey);
        idem.setCreatedAt(LocalDateTime.now());
        idem.setExpiredAt(LocalDateTime.now().plusHours(WorkflowConstants.IDEMPOTENCY_EXPIRE_HOURS));
        wfIdempotencyMapper.insert(idem);
    }

    private void notifyHandler(String businessType, WfInstance instance,
                               String actionType, String operatorName, String comment) {
        if (!handlerRegistry.hasHandler(businessType)) {
            log.debug("No business handler registered for type: {}", businessType);
            return;
        }
        try {
            WorkflowContext ctx = new WorkflowContext();
            ctx.setInstance(instance);
            ctx.setActionType(actionType);
            ctx.setOperatorName(operatorName);
            ctx.setComment(comment);
            var handler = handlerRegistry.get(businessType);
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
        } catch (Exception e) {
            log.error("Business handler error for type={}, action={}", businessType, actionType, e);
        }
    }

    // ───────────────────── QUERY METHODS ─────────────────────

    public List<String> getAvailableActions(Long instanceId, Long userId) {
        WfInstance instance = wfInstanceMapper.selectById(instanceId);
        if (instance == null) return List.of();

        List<String> actions = new ArrayList<>();

        if (WorkflowConstants.INSTANCE_RUNNING.equals(instance.getInstanceStatus())) {
            if (instance.getInitiatorId().equals(userId)) {
                actions.add(WorkflowConstants.UI_WITHDRAW);
            }
            // Check if user has pending tasks
            long pendingCount = wfTaskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getInstanceId, instanceId)
                    .eq(WfTask::getApproverId, userId)
                    .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
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
