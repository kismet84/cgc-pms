package com.cgcpms.workflow.service;

import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Converts workflow entities to VOs.
 * Extracted from WorkflowQueryService to keep it under 300 lines.
 */
@Component
@RequiredArgsConstructor
public class WorkflowVOAssembler {

    private final SysUserMapper sysUserMapper;

    // ── WfTask → WfTaskVO ──

    WfTaskVO toTaskVO(WfTask task) {
        WfTaskVO vo = new WfTaskVO();
        vo.setId(String.valueOf(task.getId()));
        vo.setInstanceId(String.valueOf(task.getInstanceId()));
        vo.setNodeInstanceId(String.valueOf(task.getNodeInstanceId()));
        vo.setApproverId(String.valueOf(task.getApproverId()));
        vo.setApproverName(task.getApproverName());
        vo.setTaskStatus(task.getTaskStatus());
        vo.setRoundNo(task.getRoundNo());
        vo.setTaskVersion(task.getTaskVersion());
        if (task.getReceivedAt() != null) vo.setReceivedAt(DateTimeUtils.DTF.format(task.getReceivedAt()));
        if (task.getHandledAt() != null) vo.setHandledAt(DateTimeUtils.DTF.format(task.getHandledAt()));
        vo.setActionType(task.getActionType());
        vo.setComment(task.getComment());
        return vo;
    }

    // ── WfRecord → WfRecordVO ──

    WfRecordVO toRecordVO(WfRecord record) {
        WfRecordVO vo = new WfRecordVO();
        vo.setId(String.valueOf(record.getId()));
        vo.setInstanceId(String.valueOf(record.getInstanceId()));
        if (record.getNodeInstanceId() != null) vo.setNodeInstanceId(String.valueOf(record.getNodeInstanceId()));
        if (record.getTaskId() != null) vo.setTaskId(String.valueOf(record.getTaskId()));
        vo.setRoundNo(record.getRoundNo());
        vo.setNodeCode(record.getNodeCode());
        vo.setNodeName(record.getNodeName());
        vo.setActionType(record.getActionType());
        vo.setActionName(record.getActionName());
        vo.setOperatorId(String.valueOf(record.getOperatorId()));
        vo.setOperatorName(record.getOperatorName());
        vo.setComment(record.getComment());
        vo.setRecordStatus(record.getRecordStatus());
        if (record.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(record.getCreatedAt()));
        vo.setBusinessType(record.getBusinessType());
        if (record.getBusinessId() != null) vo.setBusinessId(String.valueOf(record.getBusinessId()));
        return vo;
    }

    // ── WfNodeInstance → WfNodeVO (without tasks) ──

    WfNodeVO toNodeVO(WfNodeInstance node) {
        WfNodeVO vo = new WfNodeVO();
        vo.setId(String.valueOf(node.getId()));
        if (node.getTemplateNodeId() != null) vo.setTemplateNodeId(String.valueOf(node.getTemplateNodeId()));
        vo.setNodeCode(node.getNodeCode());
        vo.setNodeName(node.getNodeName());
        vo.setNodeOrder(node.getNodeOrder());
        vo.setApproveMode(node.getApproveMode());
        vo.setNodeStatus(node.getNodeStatus());
        vo.setRoundNo(node.getRoundNo());
        if (node.getStartedAt() != null) vo.setStartedAt(DateTimeUtils.DTF.format(node.getStartedAt()));
        if (node.getEndedAt() != null) vo.setEndedAt(DateTimeUtils.DTF.format(node.getEndedAt()));
        return vo;
    }

    // ── WfInstance → WfInstanceVO (core fields only, no nodes/records) ──

    WfInstanceVO toInstanceVO(WfInstance instance) {
        WfInstanceVO vo = new WfInstanceVO();
        vo.setId(String.valueOf(instance.getId()));
        vo.setTemplateId(String.valueOf(instance.getTemplateId()));
        vo.setBusinessType(instance.getBusinessType());
        vo.setBusinessId(String.valueOf(instance.getBusinessId()));
        if (instance.getProjectId() != null) vo.setProjectId(String.valueOf(instance.getProjectId()));
        if (instance.getContractId() != null) vo.setContractId(String.valueOf(instance.getContractId()));
        vo.setTitle(instance.getTitle());
        if (instance.getAmount() != null) vo.setAmount(instance.getAmount().toPlainString());
        vo.setInstanceStatus(instance.getInstanceStatus());
        vo.setCurrentRound(instance.getCurrentRound());
        vo.setResubmitCount(instance.getResubmitCount());
        vo.setInitiatorId(String.valueOf(instance.getInitiatorId()));
        SysUser initiator = sysUserMapper.selectById(instance.getInitiatorId());
        vo.setInitiatorName(initiator != null
                ? (initiator.getRealName() != null ? initiator.getRealName() : initiator.getUsername())
                : "");
        vo.setBusinessSummary(instance.getBusinessSummary());
        if (instance.getStartedAt() != null) vo.setStartedAt(DateTimeUtils.DTF.format(instance.getStartedAt()));
        if (instance.getEndedAt() != null) vo.setEndedAt(DateTimeUtils.DTF.format(instance.getEndedAt()));
        return vo;
    }

    // ── WfCc → WfCcVO ──

    WfCcVO toCcVO(WfCc cc) {
        WfCcVO vo = new WfCcVO();
        vo.setId(String.valueOf(cc.getId()));
        vo.setInstanceId(String.valueOf(cc.getInstanceId()));
        vo.setCcUserId(String.valueOf(cc.getCcUserId()));
        vo.setCcUserName(cc.getCcUserName());
        vo.setBusinessType(cc.getBusinessType());
        if (cc.getBusinessId() != null) vo.setBusinessId(String.valueOf(cc.getBusinessId()));
        vo.setTitle(cc.getTitle());
        vo.setIsRead(cc.getIsRead());
        if (cc.getCreatedTime() != null) vo.setCreatedTime(DateTimeUtils.DTF.format(cc.getCreatedTime()));
        return vo;
    }

    // ── WfTemplate → WfTemplateVO ──

    WfTemplateVO toTemplateVO(WfTemplate template) {
        WfTemplateVO vo = new WfTemplateVO();
        vo.setId(String.valueOf(template.getId()));
        vo.setTemplateCode(template.getTemplateCode());
        vo.setTemplateName(template.getTemplateName());
        vo.setBusinessType(template.getBusinessType());
        vo.setEnabled(template.getEnabled());
        vo.setAmountMin(template.getAmountMin());
        vo.setAmountMax(template.getAmountMax());
        vo.setConditionRule(template.getConditionRule());
        vo.setFormSchema(template.getFormSchema());
        vo.setRemark(template.getRemark());
        vo.setUpdatedAt(template.getUpdatedAt());
        return vo;
    }

    // ── WfTemplateNode → WfTemplateNodeVO ──

    WfTemplateNodeVO toTemplateNodeVO(WfTemplateNode node) {
        WfTemplateNodeVO vo = new WfTemplateNodeVO();
        vo.setId(String.valueOf(node.getId()));
        vo.setTemplateId(String.valueOf(node.getTemplateId()));
        vo.setNodeCode(node.getNodeCode());
        vo.setNodeName(node.getNodeName());
        vo.setNodeOrder(node.getNodeOrder());
        vo.setNodeType(node.getNodeType());
        vo.setApproveMode(node.getApproveMode());
        vo.setApproverConfig(node.getApproverConfig());
        vo.setPassRuleJson(node.getPassRuleJson());
        vo.setRejectRuleJson(node.getRejectRuleJson());
        vo.setConditionRule(node.getConditionRule());
        vo.setNodeConfig(node.getNodeConfig());
        vo.setAllowTransfer(node.getAllowTransfer());
        vo.setAllowAddSign(node.getAllowAddSign());
        vo.setTimeoutHours(node.getTimeoutHours());
        vo.setRemark(node.getRemark());
        return vo;
    }
}
