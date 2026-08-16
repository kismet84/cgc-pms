package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.WorkflowSubmissionPolicy;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfNodeInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfNodeInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
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

    // ───────────────────── PERMISSION ─────────────────────

    /**
     * Validate that the current user has the required permission (or ADMIN/SUPER_ADMIN role)
     * to submit a workflow of the given business type.
     */
    public void checkSubmitPermission(String businessType) {
        WorkflowSubmissionPolicy.requireGenericEntryAllowed(businessType);
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
        return WorkflowSubmissionPolicy.requiredPermission(businessType);
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

    public WfInstance submitCostTarget(Long userId, String username, Long tenantId,
                                       String businessType, Long businessId,
                                       String title, java.math.BigDecimal amount,
                                       Long projectId, Long contractId,
                                       String businessSummary, String variables,
                                       List<Long> ccUserIds) {
        return submitService.submitCostTarget(userId, username, tenantId,
                businessType, businessId, title, amount,
                projectId, contractId, businessSummary, variables, ccUserIds);
    }

    public WfInstance submitCostCorrectiveAction(Long userId, String username, Long tenantId,
                                                 String businessType, Long businessId,
                                                 String title, java.math.BigDecimal amount,
                                                 Long projectId, Long contractId,
                                                 String businessSummary, String variables,
                                                 List<Long> ccUserIds) {
        return submitService.submitCostCorrectiveAction(userId, username, tenantId,
                businessType, businessId, title, amount,
                projectId, contractId, businessSummary, variables, ccUserIds);
    }

    public WfInstance submitProjectBudget(Long userId, String username, Long tenantId,
                                          String businessType, Long businessId,
                                          String title, java.math.BigDecimal amount,
                                          Long projectId, Long contractId,
                                          String businessSummary, String variables,
                                          List<Long> ccUserIds) {
        return submitService.submitProjectBudget(userId, username, tenantId,
                businessType, businessId, title, amount,
                projectId, contractId, businessSummary, variables, ccUserIds);
    }

    public WfInstance submitProductionMeasurement(Long userId, String username, Long tenantId,
                                                  String businessType, Long businessId,
                                                  String title, java.math.BigDecimal amount,
                                                  Long projectId, Long contractId,
                                                  String businessSummary, String variables,
                                                  List<Long> ccUserIds) {
        return submitService.submitProductionMeasurement(userId, username, tenantId,
                businessType, businessId, title, amount,
                projectId, contractId, businessSummary, variables, ccUserIds);
    }

    public WfInstance submitPurchaseRequest(Long userId, String username, Long tenantId,
                                             String businessType, Long businessId, String title,
                                             java.math.BigDecimal amount, Long projectId, Long contractId,
                                             String businessSummary, String variables, List<Long> ccUserIds) {
        return submitService.submitPurchaseRequest(userId, username, tenantId, businessType, businessId,
                title, amount, projectId, contractId, businessSummary, variables, ccUserIds);
    }

    public WfInstance submitPurchaseOrder(Long userId, String username, Long tenantId,
                                           String businessType, Long businessId, String title,
                                           java.math.BigDecimal amount, Long projectId, Long contractId,
                                           String businessSummary, String variables, List<Long> ccUserIds) {
        return submitService.submitPurchaseOrder(userId, username, tenantId, businessType, businessId,
                title, amount, projectId, contractId, businessSummary, variables, ccUserIds);
    }

    public WfInstance submitMaterialReceipt(Long userId, String username, Long tenantId,
                                             String businessType, Long businessId, String title,
                                             java.math.BigDecimal amount, Long projectId, Long contractId,
                                             String businessSummary, String variables, List<Long> ccUserIds) {
        return submitService.submitMaterialReceipt(userId, username, tenantId, businessType, businessId,
                title, amount, projectId, contractId, businessSummary, variables, ccUserIds);
    }

    public WfInstance submitBidCostTargetTransfer(Long userId, String username, Long tenantId,
                                                   String businessType, Long businessId, String title,
                                                   java.math.BigDecimal amount, Long projectId, Long contractId,
                                                   String businessSummary, String variables, List<Long> ccUserIds) {
        return submitService.submitBidCostTargetTransfer(userId, username, tenantId, businessType, businessId,
                title, amount, projectId, contractId, businessSummary, variables, ccUserIds);
    }

    public WfInstance submitFinanceCostAllocation(Long userId, String username, Long tenantId,
                                                   String businessType, Long businessId, String title,
                                                   java.math.BigDecimal amount, Long projectId, Long contractId,
                                                   String businessSummary, String variables, List<Long> ccUserIds) {
        return submitService.submitFinanceCostAllocation(userId, username, tenantId, businessType, businessId,
                title, amount, projectId, contractId, businessSummary, variables, ccUserIds);
    }

    public WfInstance submitQualityRectification(Long userId, String username, Long tenantId,
                                                  String businessType, Long businessId, String title,
                                                  java.math.BigDecimal amount, Long projectId, Long contractId,
                                                  String businessSummary, String variables, List<Long> ccUserIds) {
        return submitService.submitQualityRectification(userId, username, tenantId, businessType, businessId,
                title, amount, projectId, contractId, businessSummary, variables, ccUserIds);
    }

    public WfInstance submitQualityConsequence(Long userId, String username, Long tenantId,
                                                String businessType, Long businessId, String title,
                                                java.math.BigDecimal amount, Long projectId, Long contractId,
                                                String businessSummary, String variables, List<Long> ccUserIds) {
        return submitService.submitQualityConsequence(userId, username, tenantId, businessType, businessId,
                title, amount, projectId, contractId, businessSummary, variables, ccUserIds);
    }

    public WfInstance submitCostGovernance(Long userId, String username, Long tenantId,
                                            String businessType, Long businessId, String title,
                                            java.math.BigDecimal amount, Long projectId, Long contractId,
                                            String businessSummary, String variables, List<Long> ccUserIds) {
        return submitService.submitCostGovernance(userId, username, tenantId, businessType, businessId,
                title, amount, projectId, contractId, businessSummary, variables, ccUserIds);
    }

    // ───────────────────── RESUBMIT ─────────────────────

    public WfInstance resubmit(Long instanceId, Long userId, String username) {
        return submitService.resubmit(instanceId, userId, username);
    }

    public WfInstance resubmitCostTarget(Long instanceId, Long userId, String username) {
        return submitService.resubmitCostTarget(instanceId, userId, username);
    }

    public WfInstance resubmitCostCorrectiveAction(Long instanceId, Long userId, String username) {
        return submitService.resubmitCostCorrectiveAction(instanceId, userId, username);
    }

    public WfInstance resubmitProjectBudget(Long instanceId, Long userId, String username) {
        return submitService.resubmitProjectBudget(instanceId, userId, username);
    }

    public WfInstance resubmitProductionMeasurement(Long instanceId, Long userId, String username) {
        return submitService.resubmitProductionMeasurement(instanceId, userId, username);
    }

    public WfInstance resubmitPurchaseRequest(Long instanceId, Long userId, String username) {
        return submitService.resubmitPurchaseRequest(instanceId, userId, username);
    }

    public WfInstance resubmitPurchaseOrder(Long instanceId, Long userId, String username) {
        return submitService.resubmitPurchaseOrder(instanceId, userId, username);
    }

    public WfInstance resubmitMaterialReceipt(Long instanceId, Long userId, String username) {
        return submitService.resubmitMaterialReceipt(instanceId, userId, username);
    }

    public WfInstance resubmitBidCostTargetTransfer(Long instanceId, Long userId, String username) {
        return submitService.resubmitBidCostTargetTransfer(instanceId, userId, username);
    }

    public WfInstance resubmitFinanceCostAllocation(Long instanceId, Long userId, String username) {
        return submitService.resubmitFinanceCostAllocation(instanceId, userId, username);
    }

    public WfInstance resubmitQualityRectification(Long instanceId, Long userId, String username) {
        return submitService.resubmitQualityRectification(instanceId, userId, username);
    }

    public WfInstance resubmitQualityConsequence(Long instanceId, Long userId, String username) {
        return submitService.resubmitQualityConsequence(instanceId, userId, username);
    }

    public WfInstance resubmitCostGovernance(Long instanceId, Long userId, String username) {
        return submitService.resubmitCostGovernance(instanceId, userId, username);
    }

    // ───────────────────── APPROVE ─────────────────────

    public void approve(Long taskId, Long userId, String username,
                        String comment, String idempotencyKey) {
        approvalService.approve(taskId, userId, username, comment, idempotencyKey);
    }

    public void approvePurchaseRequest(Long taskId, Long userId, String username,
                                       String comment, String idempotencyKey) {
        approvalService.approvePurchaseRequest(taskId, userId, username, comment, idempotencyKey);
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
                WfNodeInstance node = wfNodeInstanceMapper.selectById(pendingTasks.get(0).getNodeInstanceId());
                if (node != null && Integer.valueOf(1).equals(node.getAllowTransfer())) {
                    actions.add(WorkflowConstants.UI_TRANSFER);
                }
                if (node != null && Integer.valueOf(1).equals(node.getAllowAddSign())) {
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

}
