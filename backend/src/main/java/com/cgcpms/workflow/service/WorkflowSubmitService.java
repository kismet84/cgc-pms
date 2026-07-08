package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles submit and resubmit workflow operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowSubmitService {

    private final WorkflowCoreService core;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfNodeInstanceMapper wfNodeInstanceMapper;
    private final WfTaskMapper wfTaskMapper;
    private final WfCcService wfCcService;
    private final WorkflowBusinessAccessValidator businessAccessValidator;

    @Transactional(rollbackFor = Exception.class)
    public WfInstance submit(Long userId, String username, Long tenantId,
                             String businessType, Long businessId,
                             String title, java.math.BigDecimal amount,
                             Long projectId, Long contractId,
                             String businessSummary, String variables,
                             List<Long> ccUserIds) {

        businessAccessValidator.validateSubmit(businessType, businessId, tenantId, projectId, contractId);

        WfTemplate template = core.findTemplate(businessType, tenantId, amount);
        List<WfTemplateNode> templateNodes = core.findTemplateNodes(template.getId());

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

        // Check for active duplicate business key — V75 added deleted_flag to
        // uk_wf_instance_business, so soft-deleted rows no longer block new submissions.
        // Standard MyBatis-Plus query auto-filters deleted_flag=0.
        long activeCount = wfInstanceMapper.selectCount(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBusinessType, businessType)
                .eq(WfInstance::getBusinessId, businessId));
        if (activeCount > 0) {
            throw new BusinessException("WORKFLOW_INSTANCE_EXISTS", "该业务已提交审批，请勿重复提交");
        }

        wfInstanceMapper.insert(instance);

        // Create node instances
        List<WfNodeInstance> nodeInstances = createNodeInstances(templateNodes, instance.getId(), tenantId, 1);

        // Activate first node and create tasks
        if (!nodeInstances.isEmpty()) {
            WfNodeInstance firstNode = nodeInstances.get(0);
            core.activateNode(firstNode, templateNodes.get(0), userId, username, tenantId);
        }

        // Write submit record
        core.writeRecord(instance.getTenantId(), instance.getBusinessType(), instance.getBusinessId(),
                instance.getId(), null, null, 1, null, null,
                WorkflowConstants.ACTION_SUBMIT, "提交审批",
                userId, username, null);

        // Notify business handler
        core.notifyHandler(businessType, instance, WorkflowConstants.ACTION_SUBMIT, username, null);

        // Create cc records and notifications (if ccUserIds provided)
        if (ccUserIds != null && !ccUserIds.isEmpty()) {
            wfCcService.createCc(instance.getId(), ccUserIds, tenantId);
        }

        // Notify approvers
        notifyApprovers(instance.getId(), tenantId,
                username + "提交了审批", username + "提交了审批：" + instance.getTitle());

        return instance;
    }

    @Transactional(rollbackFor = Exception.class)
    public WfInstance resubmit(Long instanceId, Long userId, String username) {

        WfInstance tenantProbe = wfInstanceMapper.selectByIdIgnoringTenant(instanceId);
        if (tenantProbe == null) {
            throw new BusinessException("INSTANCE_NOT_FOUND", "审批实例不存在");
        }
        core.requireCurrentTenant(tenantProbe.getTenantId());
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

        // Cancel any stale pending tasks from previous rounds
        core.cancelAllPendingTasks(instanceId);

        // Create fresh node instances for the new round
        List<WfTemplateNode> templateNodes = core.findTemplateNodes(instance.getTemplateId());
        List<WfNodeInstance> newNodes = createNodeInstances(templateNodes, instanceId, instance.getTenantId(), newRound);

        // Activate the first node of the new round
        WfNodeInstance firstNode = newNodes.get(0);
        core.activateNode(firstNode, templateNodes.get(0), userId, username, instance.getTenantId());

        core.writeRecord(instance.getTenantId(), instance.getBusinessType(), instance.getBusinessId(),
                instanceId, null, null, newRound,
                null, null, WorkflowConstants.ACTION_RESUBMIT, "重新提交",
                userId, username, null);

        // Notify approvers for the new round
        notifyApprovers(instanceId, instance.getTenantId(),
                username + "重新提交了审批", username + "重新提交了审批：" + instance.getTitle());

        return instance;
    }

    // ──────────────────────── Extracted helpers ────────────────────────

    private List<WfNodeInstance> createNodeInstances(List<WfTemplateNode> nodes, Long instanceId,
                                                     Long tenantId, int roundNo) {
        List<WfNodeInstance> nodeInstances = new ArrayList<>();
        for (WfTemplateNode tn : nodes) {
            WfNodeInstance ni = new WfNodeInstance();
            ni.setTenantId(tenantId);
            ni.setInstanceId(instanceId);
            ni.setTemplateNodeId(tn.getId());
            ni.setNodeCode(tn.getNodeCode());
            ni.setNodeName(tn.getNodeName());
            ni.setNodeOrder(tn.getNodeOrder());
            ni.setApproveMode(tn.getApproveMode());
            ni.setNodeStatus(WorkflowConstants.NODE_WAITING);
            ni.setRoundNo(roundNo);
            ni.setPassRuleJson(tn.getPassRuleJson());
            ni.setRejectRuleJson(tn.getRejectRuleJson());
            wfNodeInstanceMapper.insert(ni);
            nodeInstances.add(ni);
        }
        return nodeInstances;
    }

    private void notifyApprovers(Long instanceId, Long tenantId, String title, String content) {
        List<WfTask> pendingTasks = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instanceId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        for (WfTask t : pendingTasks) {
            try {
                core.notificationService.create(tenantId, t.getApproverId(),
                        title, content,
                        "WORKFLOW", instanceId);
            } catch (Exception e) {
                log.warn("Failed to create notification for approver {}: {}", t.getApproverId(), e.getMessage());
            }
        }
    }
}
