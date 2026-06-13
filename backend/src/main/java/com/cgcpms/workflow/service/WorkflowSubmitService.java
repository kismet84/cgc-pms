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

    @Transactional
    public WfInstance submit(Long userId, String username, Long tenantId,
                             String businessType, Long businessId,
                             String title, java.math.BigDecimal amount,
                             Long projectId, Long contractId,
                             String businessSummary, String variables,
                             List<Long> ccUserIds) {

        WfTemplate template = core.findTemplate(businessType);
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
        List<WfTask> pendingTasks = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        for (WfTask t : pendingTasks) {
            try {
                core.notificationService.create(tenantId, t.getApproverId(),
                        username + "提交了审批",
                        username + "提交了审批：" + instance.getTitle(),
                        "WORKFLOW", instance.getId());
            } catch (Exception e) {
                log.warn("Failed to create submit notification for approver {}: {}", t.getApproverId(), e.getMessage());
            }
        }

        return instance;
    }

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
        WfNodeInstance rejectedNode = core.findRejectedOrLastNode(instanceId);
        if (rejectedNode != null) {
            WfTemplateNode tplNode = core.wfTemplateNodeMapper.selectById(rejectedNode.getTemplateNodeId());
            core.reactivateNode(rejectedNode, tplNode, userId, username, instance.getTenantId(), newRound);
        }

        core.writeRecord(instance.getTenantId(), instance.getBusinessType(), instance.getBusinessId(),
                instanceId, null, null, newRound,
                null, null, WorkflowConstants.ACTION_RESUBMIT, "重新提交",
                userId, username, null);

        return instance;
    }
}
