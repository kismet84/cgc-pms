package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import com.cgcpms.workflow.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowQueryService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WfTaskMapper wfTaskMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfNodeInstanceMapper wfNodeInstanceMapper;
    private final WfRecordMapper wfRecordMapper;
    private final WfTemplateMapper wfTemplateMapper;
    private final WorkflowEngine workflowEngine;

    public IPage<WfTaskVO> getMyTodos(Long userId, long pageNo, long pageSize) {
        LambdaQueryWrapper<WfTask> wrapper = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getApproverId, userId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                .orderByDesc(WfTask::getReceivedAt);

        Page<WfTask> page = wfTaskMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        return page.convert(task -> {
            WfTaskVO vo = new WfTaskVO();
            vo.setId(String.valueOf(task.getId()));
            vo.setInstanceId(String.valueOf(task.getInstanceId()));
            vo.setNodeInstanceId(String.valueOf(task.getNodeInstanceId()));
            vo.setBusinessType(task.getBusinessType());
            vo.setBusinessId(String.valueOf(task.getBusinessId()));
            vo.setApproverId(String.valueOf(task.getApproverId()));
            vo.setApproverName(task.getApproverName());
            vo.setTaskStatus(task.getTaskStatus());
            vo.setRoundNo(task.getRoundNo());
            vo.setTaskVersion(task.getTaskVersion());
            if (task.getReceivedAt() != null) vo.setReceivedAt(DTF.format(task.getReceivedAt()));
            if (task.getHandledAt() != null) vo.setHandledAt(DTF.format(task.getHandledAt()));
            vo.setActionType(task.getActionType());
            vo.setComment(task.getComment());

            // Enrich with instance info
            WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
            if (instance != null) {
                vo.setTitle(instance.getTitle());
                vo.setInstanceStatus(instance.getInstanceStatus());
            }
            return vo;
        });
    }

    public WfInstanceVO getInstanceDetail(Long instanceId, Long currentUserId) {
        WfInstance instance = wfInstanceMapper.selectById(instanceId);
        if (instance == null) return null;

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
        vo.setInitiatorName("U" + instance.getInitiatorId());
        vo.setBusinessSummary(instance.getBusinessSummary());
        if (instance.getStartedAt() != null) vo.setStartedAt(DTF.format(instance.getStartedAt()));
        if (instance.getEndedAt() != null) vo.setEndedAt(DTF.format(instance.getEndedAt()));

        WfTemplate template = wfTemplateMapper.selectById(instance.getTemplateId());
        if (template != null) {
            vo.setTemplateName(template.getTemplateName());
        }

        // Available actions
        vo.setAvailableActions(workflowEngine.getAvailableActions(instanceId, currentUserId));

        // Nodes with tasks
        List<WfNodeInstance> nodes = wfNodeInstanceMapper.selectList(
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, instanceId)
                        .orderByAsc(WfNodeInstance::getNodeOrder));
        List<WfNodeVO> nodeVOs = new ArrayList<>();
        for (WfNodeInstance n : nodes) {
            WfNodeVO nvo = new WfNodeVO();
            nvo.setId(String.valueOf(n.getId()));
            if (n.getTemplateNodeId() != null) nvo.setTemplateNodeId(String.valueOf(n.getTemplateNodeId()));
            nvo.setNodeCode(n.getNodeCode());
            nvo.setNodeName(n.getNodeName());
            nvo.setNodeOrder(n.getNodeOrder());
            nvo.setApproveMode(n.getApproveMode());
            nvo.setNodeStatus(n.getNodeStatus());
            nvo.setRoundNo(n.getRoundNo());
            if (n.getStartedAt() != null) nvo.setStartedAt(DTF.format(n.getStartedAt()));
            if (n.getEndedAt() != null) nvo.setEndedAt(DTF.format(n.getEndedAt()));

            // Tasks for this node
            List<WfTask> tasks = wfTaskMapper.selectList(
                    new LambdaQueryWrapper<WfTask>()
                            .eq(WfTask::getNodeInstanceId, n.getId())
                            .orderByDesc(WfTask::getReceivedAt));
            List<WfTaskVO> taskVOs = tasks.stream().map(t -> {
                WfTaskVO tvo = new WfTaskVO();
                tvo.setId(String.valueOf(t.getId()));
                tvo.setInstanceId(String.valueOf(t.getInstanceId()));
                tvo.setNodeInstanceId(String.valueOf(t.getNodeInstanceId()));
                tvo.setApproverId(String.valueOf(t.getApproverId()));
                tvo.setApproverName(t.getApproverName());
                tvo.setTaskStatus(t.getTaskStatus());
                tvo.setRoundNo(t.getRoundNo());
                tvo.setTaskVersion(t.getTaskVersion());
                if (t.getReceivedAt() != null) tvo.setReceivedAt(DTF.format(t.getReceivedAt()));
                if (t.getHandledAt() != null) tvo.setHandledAt(DTF.format(t.getHandledAt()));
                tvo.setActionType(t.getActionType());
                tvo.setComment(t.getComment());
                return tvo;
            }).collect(Collectors.toList());
            nvo.setTasks(taskVOs);
            nodeVOs.add(nvo);
        }
        vo.setNodes(nodeVOs);

        // Records
        List<WfRecord> records = wfRecordMapper.selectList(
                new LambdaQueryWrapper<WfRecord>()
                        .eq(WfRecord::getInstanceId, instanceId)
                        .orderByAsc(WfRecord::getRoundNo)
                        .orderByAsc(WfRecord::getCreatedAt));
        List<WfRecordVO> recordVOs = records.stream().map(r -> {
            WfRecordVO rvo = new WfRecordVO();
            rvo.setId(String.valueOf(r.getId()));
            rvo.setInstanceId(String.valueOf(r.getInstanceId()));
            if (r.getNodeInstanceId() != null) rvo.setNodeInstanceId(String.valueOf(r.getNodeInstanceId()));
            if (r.getTaskId() != null) rvo.setTaskId(String.valueOf(r.getTaskId()));
            rvo.setRoundNo(r.getRoundNo());
            rvo.setNodeCode(r.getNodeCode());
            rvo.setNodeName(r.getNodeName());
            rvo.setActionType(r.getActionType());
            rvo.setActionName(r.getActionName());
            rvo.setOperatorId(String.valueOf(r.getOperatorId()));
            rvo.setOperatorName(r.getOperatorName());
            rvo.setComment(r.getComment());
            rvo.setRecordStatus(r.getRecordStatus());
            if (r.getCreatedAt() != null) rvo.setCreatedAt(DTF.format(r.getCreatedAt()));
            return rvo;
        }).collect(Collectors.toList());
        vo.setRecords(recordVOs);

        return vo;
    }
}
