package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import com.cgcpms.workflow.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
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
    private final WorkflowEngine workflowEngine;
    private final WorkflowVOAssembler voAssembler;
    private final ProjectAccessChecker projectAccessChecker;

    private static final Set<String> SUPPORTED_BUSINESS_TYPES = Set.of(
            WorkflowBusinessTypes.CONTRACT_APPROVAL,
            WorkflowBusinessTypes.PURCHASE_ORDER,
            WorkflowBusinessTypes.MATERIAL_RECEIPT,
            WorkflowBusinessTypes.SUB_MEASURE,
            WorkflowBusinessTypes.PAY_REQUEST,
            WorkflowBusinessTypes.VAR_ORDER,
            WorkflowBusinessTypes.PURCHASE_REQUEST,
            WorkflowBusinessTypes.CT_CHANGE,
            WorkflowBusinessTypes.SETTLEMENT,
            WorkflowBusinessTypes.COST_TARGET,
            WorkflowBusinessTypes.CONTRACT_REVENUE,
            WorkflowBusinessTypes.MATERIAL_REQUISITION,
            WorkflowBusinessTypes.TECH_ITEM
    );

    private static final Set<String> SUPPORTED_INSTANCE_STATUSES = Set.of(
            WorkflowConstants.INSTANCE_RUNNING,
            WorkflowConstants.INSTANCE_APPROVED,
            WorkflowConstants.INSTANCE_REJECTED,
            WorkflowConstants.INSTANCE_WITHDRAWN,
            WorkflowConstants.INSTANCE_VOIDED
    );

    // ── 我的待办 ──

    public IPage<WfTaskVO> getMyTodos(Long tenantId, Long userId, long pageNo, long pageSize) {
        return getMyTodos(tenantId, userId, null, null, null, null, null, pageNo, pageSize);
    }

    public IPage<WfTaskVO> getMyTodos(Long tenantId, Long userId,
                                      String keyword, String businessType, String instanceStatus,
                                      LocalDateTime startTime, LocalDateTime endTime,
                                      long pageNo, long pageSize) {
        if (isUnsupportedBusinessType(businessType) || isUnsupportedInstanceStatus(instanceStatus)) {
            return emptyPage(pageNo, pageSize);
        }
        String normalizedBusinessType = trimToNull(businessType);
        Set<Long> instanceIds = resolveInstanceIds(tenantId, keyword, normalizedBusinessType, instanceStatus);
        if (instanceIds != null && instanceIds.isEmpty()) {
            return emptyPage(pageNo, pageSize);
        }

        // 查询运行中的实例 ID（兼容 H2 和 MySQL）
        Set<Long> runningInstanceIds = wfInstanceMapper.selectList(
                new LambdaQueryWrapper<WfInstance>()
                        .select(WfInstance::getId)
                        .eq(WfInstance::getTenantId, tenantId)
                        .eq(WfInstance::getInstanceStatus, WorkflowConstants.INSTANCE_RUNNING))
                .stream().map(WfInstance::getId).collect(Collectors.toSet());
        if (runningInstanceIds.isEmpty()) {
            return emptyPage(pageNo, pageSize);
        }

        LambdaQueryWrapper<WfTask> wrapper = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTenantId, tenantId)
                .eq(WfTask::getApproverId, userId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                .in(WfTask::getInstanceId, runningInstanceIds);
        if (normalizedBusinessType != null) {
            wrapper.eq(WfTask::getBusinessType, normalizedBusinessType);
        }
        if (instanceIds != null) {
            wrapper.in(WfTask::getInstanceId, instanceIds);
        }
        if (startTime != null) {
            wrapper.ge(WfTask::getReceivedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(WfTask::getReceivedAt, endTime);
        }
        wrapper.orderByDesc(WfTask::getReceivedAt);

        Page<WfTask> page = wfTaskMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        Map<Long, WfInstance> instanceMap = batchLoadInstances(
                page.getRecords(), WfTask::getInstanceId, tenantId);

        return page.convert(task -> {
            WfTaskVO vo = voAssembler.toTaskVO(task);
            vo.setBusinessType(task.getBusinessType());
            vo.setBusinessId(String.valueOf(task.getBusinessId()));
            enrichTaskWithInstance(vo, instanceMap.get(task.getInstanceId()));
            return vo;
        });
    }

    // ── 我发起的实例 ──

    public IPage<WfMyInstanceVO> getMyStarted(Long tenantId, Long userId, long pageNo, long pageSize) {
        return getMyStarted(tenantId, userId, null, pageNo, pageSize);
    }

    public IPage<WfMyInstanceVO> getMyStarted(Long tenantId, Long userId, String instanceStatus,
                                              long pageNo, long pageSize) {
        return getMyStarted(tenantId, userId, null, null, instanceStatus, null, null, pageNo, pageSize);
    }

    public IPage<WfMyInstanceVO> getMyStarted(Long tenantId, Long userId,
                                              String keyword, String businessType, String instanceStatus,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              long pageNo, long pageSize) {
        if (isUnsupportedBusinessType(businessType) || isUnsupportedInstanceStatus(instanceStatus)) {
            return emptyPage(pageNo, pageSize);
        }
        String normalizedKeyword = trimToNull(keyword);
        String normalizedBusinessType = trimToNull(businessType);
        String normalizedStatus = trimToNull(instanceStatus);

        LambdaQueryWrapper<WfInstance> wrapper = new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getTenantId, tenantId)
                .eq(WfInstance::getInitiatorId, userId);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(WfInstance::getTitle, normalizedKeyword)
                    .or()
                    .like(WfInstance::getBusinessSummary, normalizedKeyword));
        }
        if (normalizedBusinessType != null) {
            wrapper.eq(WfInstance::getBusinessType, normalizedBusinessType);
        }
        if (normalizedStatus != null) {
            wrapper.eq(WfInstance::getInstanceStatus, normalizedStatus);
        }
        if (startTime != null) {
            wrapper.ge(WfInstance::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(WfInstance::getCreatedAt, endTime);
        }
        wrapper.orderByDesc(WfInstance::getUpdatedAt)
                .orderByDesc(WfInstance::getCreatedAt);

        Page<WfInstance> page = wfInstanceMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Map<Long, String> currentNodeNames = batchLoadCurrentNodeNames(tenantId, page.getRecords());

        return page.convert(instance -> {
            WfMyInstanceVO vo = new WfMyInstanceVO();
            vo.setInstanceId(String.valueOf(instance.getId()));
            vo.setBusinessType(instance.getBusinessType());
            if (instance.getBusinessId() != null) vo.setBusinessId(String.valueOf(instance.getBusinessId()));
            vo.setTitle(instance.getTitle());
            vo.setInstanceStatus(instance.getInstanceStatus());
            if (instance.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(instance.getCreatedAt()));
            if (instance.getUpdatedAt() != null) vo.setUpdatedAt(DateTimeUtils.DTF.format(instance.getUpdatedAt()));
            vo.setCurrentNodeName(currentNodeNames.get(instance.getId()));
            return vo;
        });
    }

    // ── 我的已办 ──

    public IPage<WfRecordVO> getMyDone(Long userId, Long tenantId, long pageNo, long pageSize) {
        return getMyDone(userId, tenantId, null, null, null, null, null, pageNo, pageSize);
    }

    public IPage<WfRecordVO> getMyDone(Long userId, Long tenantId,
                                       String keyword, String businessType, String instanceStatus,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       long pageNo, long pageSize) {
        if (isUnsupportedBusinessType(businessType) || isUnsupportedInstanceStatus(instanceStatus)) {
            return emptyPage(pageNo, pageSize);
        }
        String normalizedBusinessType = trimToNull(businessType);
        Set<Long> instanceIds = resolveInstanceIds(tenantId, keyword, normalizedBusinessType, instanceStatus);
        if (instanceIds != null && instanceIds.isEmpty()) {
            return emptyPage(pageNo, pageSize);
        }

        LambdaQueryWrapper<WfRecord> wrapper = new LambdaQueryWrapper<WfRecord>()
                .eq(WfRecord::getTenantId, tenantId)
                .eq(WfRecord::getOperatorId, userId)
                .in(WfRecord::getActionType,
                        WorkflowConstants.ACTION_APPROVE,
                        WorkflowConstants.ACTION_REJECT,
                        WorkflowConstants.ACTION_TRANSFER,
                        WorkflowConstants.ACTION_ADD_SIGN);
        if (normalizedBusinessType != null) {
            wrapper.eq(WfRecord::getBusinessType, normalizedBusinessType);
        }
        if (instanceIds != null) {
            wrapper.in(WfRecord::getInstanceId, instanceIds);
        }
        if (startTime != null) {
            wrapper.ge(WfRecord::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(WfRecord::getCreatedAt, endTime);
        }
        wrapper.orderByDesc(WfRecord::getCreatedAt);

        Page<WfRecord> page = wfRecordMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        Map<Long, WfInstance> instanceMap = batchLoadInstances(
                page.getRecords(), WfRecord::getInstanceId, tenantId);

        return page.convert(record -> {
            WfRecordVO vo = voAssembler.toRecordVO(record);
            enrichRecordWithInstance(vo, instanceMap.get(record.getInstanceId()));
            return vo;
        });
    }

    // ── 实例详情 ──

    public WfInstanceVO getInstanceDetail(Long tenantId, Long instanceId, Long currentUserId) {
        WfInstance instance = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getTenantId, tenantId)
                .eq(WfInstance::getId, instanceId));
        if (instance == null) return null;

        if (!isAuthorized(instance, tenantId, instanceId, currentUserId)) return null;
        requireProjectAccess(instance);

        WfInstanceVO vo = voAssembler.toInstanceVO(instance);
        WfTemplate template = wfTemplateMapper.selectById(instance.getTemplateId());
        if (template != null) vo.setTemplateName(template.getTemplateName());
        vo.setAvailableActions(workflowEngine.getAvailableActions(tenantId, instanceId, currentUserId));

        // Nodes with tasks
        List<WfNodeInstance> nodes = wfNodeInstanceMapper.selectList(
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getTenantId, tenantId)
                        .eq(WfNodeInstance::getInstanceId, instanceId)
                        .orderByAsc(WfNodeInstance::getNodeOrder));

        Map<Long, List<WfTask>> tasksByNode = batchLoadTasksByNode(tenantId, nodes);
        List<WfNodeVO> nodeVOs = buildNodeVOs(nodes, tasksByNode);
        vo.setNodes(nodeVOs);

        // Records
        List<WfRecord> records = wfRecordMapper.selectList(
                new LambdaQueryWrapper<WfRecord>()
                        .eq(WfRecord::getTenantId, tenantId)
                        .eq(WfRecord::getInstanceId, instanceId)
                        .orderByAsc(WfRecord::getRoundNo)
                        .orderByAsc(WfRecord::getCreatedAt));
        vo.setRecords(records.stream().map(voAssembler::toRecordVO).collect(Collectors.toList()));

        return vo;
    }

    // ── 我的抄送 ──

    public IPage<WfCcVO> getMyCc(Long userId, Long tenantId, long pageNo, long pageSize) {
        return getMyCc(userId, tenantId, null, null, null, null, null, pageNo, pageSize);
    }

    public IPage<WfCcVO> getMyCc(Long userId, Long tenantId,
                                 String keyword, String businessType, String instanceStatus,
                                 LocalDateTime startTime, LocalDateTime endTime,
                                 long pageNo, long pageSize) {
        if (isUnsupportedBusinessType(businessType) || isUnsupportedInstanceStatus(instanceStatus)) {
            return emptyPage(pageNo, pageSize);
        }
        String normalizedBusinessType = trimToNull(businessType);
        Set<Long> instanceIds = resolveInstanceIds(tenantId, keyword, normalizedBusinessType, instanceStatus);
        if (instanceIds != null && instanceIds.isEmpty()) {
            return emptyPage(pageNo, pageSize);
        }

        LambdaQueryWrapper<WfCc> wrapper = new LambdaQueryWrapper<WfCc>()
                .eq(WfCc::getTenantId, tenantId)
                .eq(WfCc::getCcUserId, userId);
        if (normalizedBusinessType != null) {
            wrapper.eq(WfCc::getBusinessType, normalizedBusinessType);
        }
        if (instanceIds != null) {
            wrapper.in(WfCc::getInstanceId, instanceIds);
        }
        if (startTime != null) {
            wrapper.ge(WfCc::getCreatedTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(WfCc::getCreatedTime, endTime);
        }
        wrapper.orderByDesc(WfCc::getCreatedTime);

        Page<WfCc> page = wfCcMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        Map<Long, WfInstance> instanceMap = batchLoadInstances(
                page.getRecords(), WfCc::getInstanceId, tenantId);

        return page.convert(cc -> {
            WfCcVO vo = voAssembler.toCcVO(cc);
            WfInstance instance = instanceMap.get(cc.getInstanceId());
            if (instance != null) vo.setInstanceStatus(instance.getInstanceStatus());
            return vo;
        });
    }

    // ── 内部辅助方法 ──

    private Set<Long> resolveInstanceIds(Long tenantId, String keyword, String businessType, String instanceStatus) {
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = trimToNull(instanceStatus);
        String normalizedBusinessType = trimToNull(businessType);
        if (normalizedKeyword == null && normalizedStatus == null) {
            return null;
        }
        LambdaQueryWrapper<WfInstance> wrapper = new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getTenantId, tenantId);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(WfInstance::getTitle, normalizedKeyword)
                    .or()
                    .like(WfInstance::getBusinessSummary, normalizedKeyword));
        }
        if (normalizedBusinessType != null) {
            wrapper.eq(WfInstance::getBusinessType, normalizedBusinessType);
        }
        if (normalizedStatus != null) {
            wrapper.eq(WfInstance::getInstanceStatus, normalizedStatus);
        }
        return wfInstanceMapper.selectList(wrapper).stream()
                .map(WfInstance::getId)
                .collect(Collectors.toSet());
    }

    private boolean isUnsupportedBusinessType(String businessType) {
        String normalized = trimToNull(businessType);
        return normalized != null && !SUPPORTED_BUSINESS_TYPES.contains(normalized);
    }

    private boolean isUnsupportedInstanceStatus(String instanceStatus) {
        String normalized = trimToNull(instanceStatus);
        return normalized != null && !SUPPORTED_INSTANCE_STATUSES.contains(normalized);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private <T> IPage<T> emptyPage(long pageNo, long pageSize) {
        Page<T> page = new Page<>(pageNo, pageSize);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        return page;
    }

    private boolean isAuthorized(WfInstance instance, Long tenantId, Long instanceId, Long currentUserId) {
        if (instance.getInitiatorId().equals(currentUserId)) return true;
        Long count = wfTaskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTenantId, tenantId)
                .eq(WfTask::getInstanceId, instanceId)
                .eq(WfTask::getApproverId, currentUserId));
        if (count > 0) return true;
        Long ccCount = wfCcMapper.selectCount(new LambdaQueryWrapper<WfCc>()
                .eq(WfCc::getTenantId, tenantId)
                .eq(WfCc::getInstanceId, instanceId)
                .eq(WfCc::getCcUserId, currentUserId));
        if (ccCount > 0) return true;
        // ADMIN/SUPER_ADMIN may view instances in the current tenant. The instance was already loaded
        // with the caller's tenantId; this fallback only exempts the participant check for admins within
        // their own tenant, not across tenants.
        return UserContext.hasAnyRole("ADMIN", "SUPER_ADMIN");
    }

    private void requireProjectAccess(WfInstance instance) {
        if (instance.getProjectId() != null) {
            projectAccessChecker.checkAccess(instance.getProjectId(), "查看审批详情");
        }
    }

    private <T> Map<Long, WfInstance> batchLoadInstances(List<T> records,
                                                          Function<T, Long> idExtractor,
                                                          Long tenantId) {
        List<Long> instanceIds = records.stream().map(idExtractor).distinct().toList();
        if (instanceIds.isEmpty()) return Collections.emptyMap();
        return wfInstanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getTenantId, tenantId)
                        .in(WfInstance::getId, instanceIds)).stream()
                .collect(Collectors.toMap(WfInstance::getId, Function.identity()));
    }

    private Map<Long, List<WfTask>> batchLoadTasksByNode(Long tenantId, List<WfNodeInstance> nodes) {
        List<Long> nodeIds = nodes.stream().map(WfNodeInstance::getId).toList();
        if (nodeIds.isEmpty()) return Collections.emptyMap();
        List<WfTask> allTasks = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, tenantId)
                        .in(WfTask::getNodeInstanceId, nodeIds));
        return allTasks.stream().collect(Collectors.groupingBy(WfTask::getNodeInstanceId));
    }

    private Map<Long, String> batchLoadCurrentNodeNames(Long tenantId, List<WfInstance> instances) {
        List<Long> instanceIds = instances.stream().map(WfInstance::getId).toList();
        if (instanceIds.isEmpty()) return Collections.emptyMap();
        return wfNodeInstanceMapper.selectList(new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getTenantId, tenantId)
                        .in(WfNodeInstance::getInstanceId, instanceIds)
                        .eq(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_ACTIVE)
                        .orderByAsc(WfNodeInstance::getNodeOrder))
                .stream()
                .collect(Collectors.toMap(WfNodeInstance::getInstanceId,
                        WfNodeInstance::getNodeName,
                        (first, ignored) -> first));
    }

    private List<WfNodeVO> buildNodeVOs(List<WfNodeInstance> nodes,
                                        Map<Long, List<WfTask>> tasksByNode) {
        List<WfNodeVO> result = new ArrayList<>();
        for (WfNodeInstance n : nodes) {
            WfNodeVO nvo = voAssembler.toNodeVO(n);
            List<WfTask> tasks = tasksByNode.getOrDefault(n.getId(), Collections.emptyList());
            nvo.setTasks(tasks.stream().map(voAssembler::toTaskVO).collect(Collectors.toList()));
            result.add(nvo);
        }
        return result;
    }

    private void enrichTaskWithInstance(WfTaskVO vo, WfInstance instance) {
        if (instance != null) {
            vo.setTitle(instance.getTitle());
            vo.setInstanceStatus(instance.getInstanceStatus());
        }
    }

    private void enrichRecordWithInstance(WfRecordVO vo, WfInstance instance) {
        if (instance != null) {
            vo.setTitle(instance.getTitle());
            vo.setInstanceStatus(instance.getInstanceStatus());
        }
    }
}
