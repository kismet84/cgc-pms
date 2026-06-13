package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import com.cgcpms.workflow.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.cgcpms.common.util.DateTimeUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowQueryService {

    private final WfTaskMapper wfTaskMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfNodeInstanceMapper wfNodeInstanceMapper;
    private final WfRecordMapper wfRecordMapper;
    private final WfTemplateMapper wfTemplateMapper;
    private final WfCcMapper wfCcMapper;
    private final SysUserMapper sysUserMapper;
    private final WorkflowEngine workflowEngine;

    public IPage<WfTaskVO> getMyTodos(Long tenantId, Long userId, long pageNo, long pageSize) {
        LambdaQueryWrapper<WfTask> wrapper = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTenantId, tenantId)
                .eq(WfTask::getApproverId, userId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                .orderByDesc(WfTask::getReceivedAt);

        Page<WfTask> page = wfTaskMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        // Batch-fetch instances to avoid N+1
        List<Long> instanceIds = page.getRecords().stream().map(WfTask::getInstanceId).distinct().toList();
        final Map<Long, WfInstance> instanceMap;
        if (!instanceIds.isEmpty()) {
            instanceMap = wfInstanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                            .eq(WfInstance::getTenantId, tenantId)
                            .in(WfInstance::getId, instanceIds)).stream()
                    .collect(Collectors.toMap(WfInstance::getId, Function.identity()));
        } else {
            instanceMap = Collections.emptyMap();
        }

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
            if (task.getReceivedAt() != null) vo.setReceivedAt(DateTimeUtils.DTF.format(task.getReceivedAt()));
            if (task.getHandledAt() != null) vo.setHandledAt(DateTimeUtils.DTF.format(task.getHandledAt()));
            vo.setActionType(task.getActionType());
            vo.setComment(task.getComment());

            // Enrich with instance info
            WfInstance instance = instanceMap.get(task.getInstanceId());
            if (instance != null) {
                vo.setTitle(instance.getTitle());
                vo.setInstanceStatus(instance.getInstanceStatus());
            }
            return vo;
        });
    }

    public IPage<WfRecordVO> getMyDone(Long userId, Long tenantId, long pageNo, long pageSize) {
        LambdaQueryWrapper<WfRecord> wrapper = new LambdaQueryWrapper<WfRecord>()
                .eq(WfRecord::getTenantId, tenantId)
                .eq(WfRecord::getOperatorId, userId)
                .orderByDesc(WfRecord::getCreatedAt);

        Page<WfRecord> page = wfRecordMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        // Batch-fetch instances to avoid N+1
        List<Long> instanceIds = page.getRecords().stream().map(WfRecord::getInstanceId).distinct().toList();
        final Map<Long, WfInstance> instanceMap;
        if (!instanceIds.isEmpty()) {
            instanceMap = wfInstanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                            .eq(WfInstance::getTenantId, tenantId)
                            .in(WfInstance::getId, instanceIds)).stream()
                    .collect(Collectors.toMap(WfInstance::getId, Function.identity()));
        } else {
            instanceMap = Collections.emptyMap();
        }

        return page.convert(record -> {
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

            // Include businessType from record entity
            vo.setBusinessType(record.getBusinessType());

            // Enrich with instance info
            WfInstance instance = instanceMap.get(record.getInstanceId());
            if (instance != null) {
                vo.setTitle(instance.getTitle());
                vo.setInstanceStatus(instance.getInstanceStatus());
            }
            return vo;
        });
    }

    public WfInstanceVO getInstanceDetail(Long tenantId, Long instanceId, Long currentUserId) {
        WfInstance instance = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getTenantId, tenantId)
                .eq(WfInstance::getId, instanceId));
        if (instance == null) return null;

        // Authorization: only initiator, approvers, or admin can view
        boolean authorized = instance.getInitiatorId().equals(currentUserId);
        if (!authorized) {
            Long count = wfTaskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getTenantId, tenantId)
                    .eq(WfTask::getInstanceId, instanceId)
                    .eq(WfTask::getApproverId, currentUserId));
            authorized = count > 0;
        }
        // Admin role bypass: admins can view any instance
        if (!authorized && UserContext.hasAnyRole("ADMIN", "SUPER_ADMIN")) {
            authorized = true;
        }
        if (!authorized) {
            return null;
        }

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

        WfTemplate template = wfTemplateMapper.selectById(instance.getTemplateId());
        if (template != null) {
            vo.setTemplateName(template.getTemplateName());
        }

        // Available actions
        vo.setAvailableActions(workflowEngine.getAvailableActions(tenantId, instanceId, currentUserId));

        // Nodes with tasks
        List<WfNodeInstance> nodes = wfNodeInstanceMapper.selectList(
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getTenantId, tenantId)
                        .eq(WfNodeInstance::getInstanceId, instanceId)
                        .orderByAsc(WfNodeInstance::getNodeOrder));

        // Batch-fetch all tasks for all nodes to avoid N+1
        List<Long> nodeIds = nodes.stream().map(WfNodeInstance::getId).toList();
        Map<Long, List<WfTask>> tasksByNode = Collections.emptyMap();
        if (!nodeIds.isEmpty()) {
            List<WfTask> allTasks = wfTaskMapper.selectList(
                    new LambdaQueryWrapper<WfTask>()
                            .eq(WfTask::getTenantId, tenantId)
                            .in(WfTask::getNodeInstanceId, nodeIds));
            tasksByNode = allTasks.stream().collect(Collectors.groupingBy(WfTask::getNodeInstanceId));
        }

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
            if (n.getStartedAt() != null) nvo.setStartedAt(DateTimeUtils.DTF.format(n.getStartedAt()));
            if (n.getEndedAt() != null) nvo.setEndedAt(DateTimeUtils.DTF.format(n.getEndedAt()));

            // Tasks for this node
            List<WfTask> tasks = tasksByNode.getOrDefault(n.getId(), Collections.emptyList());
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
                if (t.getReceivedAt() != null) tvo.setReceivedAt(DateTimeUtils.DTF.format(t.getReceivedAt()));
                if (t.getHandledAt() != null) tvo.setHandledAt(DateTimeUtils.DTF.format(t.getHandledAt()));
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
                        .eq(WfRecord::getTenantId, tenantId)
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
            if (r.getCreatedAt() != null) rvo.setCreatedAt(DateTimeUtils.DTF.format(r.getCreatedAt()));
            return rvo;
        }).collect(Collectors.toList());
        vo.setRecords(recordVOs);

        return vo;
    }

    /**
     * 我的抄送列表（分页）。
     */
    public IPage<WfCcVO> getMyCc(Long userId, Long tenantId, long pageNo, long pageSize) {
        LambdaQueryWrapper<WfCc> wrapper = new LambdaQueryWrapper<WfCc>()
                .eq(WfCc::getTenantId, tenantId)
                .eq(WfCc::getCcUserId, userId)
                .orderByDesc(WfCc::getCreatedTime);

        Page<WfCc> page = wfCcMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        // Batch-fetch instances to avoid N+1
        List<Long> instanceIds = page.getRecords().stream().map(WfCc::getInstanceId).distinct().toList();
        final Map<Long, WfInstance> instanceMap;
        if (!instanceIds.isEmpty()) {
            instanceMap = wfInstanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                            .eq(WfInstance::getTenantId, tenantId)
                            .in(WfInstance::getId, instanceIds)).stream()
                    .collect(Collectors.toMap(WfInstance::getId, Function.identity()));
        } else {
            instanceMap = Collections.emptyMap();
        }

        return page.convert(cc -> {
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

            // Enrich with instance info
            WfInstance instance = instanceMap.get(cc.getInstanceId());
            if (instance != null) {
                vo.setInstanceStatus(instance.getInstanceStatus());
            }
            return vo;
        });
    }
}
