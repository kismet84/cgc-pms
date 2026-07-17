package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfNodeInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.entity.WfTemplateNode;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfNodeInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.workflow.mapper.WfTemplateNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final WfNodeInstanceMapper wfNodeInstanceMapper;
    private final WfTemplateNodeMapper wfTemplateNodeMapper;

    // ───────────────────── PERMISSION ─────────────────────

    /**
     * Validate that the current user has the required permission (or ADMIN/SUPER_ADMIN role)
     * to submit a workflow of the given business type.
     */
    public void checkSubmitPermission(String businessType) {
        String requiredPermission = getRequiredPermission(businessType);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new BusinessException("UNAUTHORIZED", "未认证");
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String authStr = authority.getAuthority();
            if ("ROLE_ADMIN".equals(authStr) || "ROLE_SUPER_ADMIN".equals(authStr)) {
                log.warn("ADMIN/SUPER_ADMIN bypass submitting businessType={}, userId={}, role={}",
                    businessType, null, authStr);
                return;
            }
            if (requiredPermission.equals(authStr)) {
                return;
            }
        }
        throw new BusinessException("WORKFLOW_PERMISSION_DENIED", "缺少权限: " + requiredPermission);
    }

    /**
     * Map business type to the required authority/permission code for submission.
     */
    public String getRequiredPermission(String businessType) {
        return switch (businessType) {
            case WorkflowBusinessTypes.CONTRACT_APPROVAL -> "contract:submit";
            case WorkflowBusinessTypes.CONTRACT_REVENUE -> "revenue:submit";
            case WorkflowBusinessTypes.PURCHASE_ORDER -> "purchase:order:submit";
            case WorkflowBusinessTypes.PURCHASE_REQUEST -> "purchase:request:submit";
            case WorkflowBusinessTypes.MATERIAL_RECEIPT -> "receipt:submit";
            case WorkflowBusinessTypes.SUB_MEASURE -> "subcontract:measure:submit";
            case WorkflowBusinessTypes.PAY_REQUEST -> "payment:app:submit";
            case WorkflowBusinessTypes.VAR_ORDER -> "variation:order:submit";
            case WorkflowBusinessTypes.CT_CHANGE -> "contract:change:submit";
            case WorkflowBusinessTypes.SETTLEMENT -> "settlement:submit";
            case WorkflowBusinessTypes.COST_TARGET -> "cost:target:submit";
            case WorkflowBusinessTypes.MATERIAL_REQUISITION -> "requisition:submit";
            case WorkflowBusinessTypes.PROJECT_BUDGET -> "budget:submit";
            case WorkflowBusinessTypes.EXPENSE -> "expense:submit";
            case WorkflowBusinessTypes.OWNER_SETTLEMENT -> "revenue:settlement:submit";
            case WorkflowBusinessTypes.PRODUCTION_MEASUREMENT -> "measurement:submit";
            case WorkflowBusinessTypes.PROJECT_SCHEDULE -> "schedule:submit";
            case WorkflowBusinessTypes.PROJECT_PERIOD_PLAN -> "schedule:submit";
            case WorkflowBusinessTypes.PROJECT_CORRECTIVE_ACTION -> "schedule:correct";
            case WorkflowBusinessTypes.TECHNICAL_SCHEME -> "technical:scheme:submit";
            case WorkflowBusinessTypes.PROJECT_FINAL_ACCEPTANCE -> "closeout:acceptance:submit";
            default -> throw new BusinessException("UNSUPPORTED_BUSINESS_TYPE", "不支持的业务类型: " + businessType);
        };
    }

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
            List<WfTask> pendingTasks = wfTaskMapper.selectList(pendingWrapper);
            if (!pendingTasks.isEmpty()) {
                actions.add(WorkflowConstants.UI_APPROVE);
                actions.add(WorkflowConstants.UI_REJECT);
                WfTemplateNode templateNode = findTemplateNode(pendingTasks.get(0));
                if (templateNode != null && Integer.valueOf(1).equals(templateNode.getAllowTransfer())) {
                    actions.add(WorkflowConstants.UI_TRANSFER);
                }
                if (templateNode != null && Integer.valueOf(1).equals(templateNode.getAllowAddSign())) {
                    actions.add(WorkflowConstants.UI_ADD_SIGN);
                }
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

    private WfTemplateNode findTemplateNode(WfTask task) {
        WfNodeInstance node = wfNodeInstanceMapper.selectById(task.getNodeInstanceId());
        if (node == null || node.getTemplateNodeId() == null) return null;
        return wfTemplateNodeMapper.selectById(node.getTemplateNodeId());
    }
}
