package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import com.cgcpms.workflow.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
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
    private final WorkflowBusinessCodeResolver businessCodeResolver;
    private final ProjectAccessChecker projectAccessChecker;
    private final SysUserMapper sysUserMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final Pattern BUSINESS_TYPE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Map<String, String> COST_DETAIL_KEY_CASE = List.of(
                    "versionCode", "versionName", "effectiveDate", "validationReport",
                    "ruleCode", "sourceType", "businessCategory", "projectCode",
                    "targetSubjectCode", "targetSubjectName", "effectiveFrom", "effectiveTo",
                    "sourceSubjectCode", "sourceSubjectName", "targetGroupCode", "mappingReason",
                    "requestCode", "projectName", "projectStatusSnapshot", "mainContractId", "mainContractCode",
                    "mainContractAmount", "mainContractApprovalStatus", "mainContractStatus",
                    "targetVersionNo", "targetAmount", "subjectCode",
                    "subjectName", "impactSnapshot", "batchCode", "batchType", "scopeKey",
                    "cutoffAt", "originalFactCount", "changedFactCount", "unclassifiedCount",
                    "originalTotal", "oldSubjectCode", "oldSubjectName", "newSubjectCode",
                    "newSubjectName", "differenceType", "factCount", "taxAmount",
                    "amountWithoutTax", "targetType", "targetId", "projectId", "createdAt",
                    "sourceCode", "accountingPeriod", "originalAmount", "subjectCodes",
                    "projectCodes", "reversalAmount", "amount")
            .stream().collect(Collectors.toUnmodifiableMap(
                    value -> value.toLowerCase(Locale.ROOT), Function.identity()));

    private static final Set<String> SUPPORTED_INSTANCE_STATUSES = Set.of(
            WorkflowConstants.INSTANCE_RUNNING,
            WorkflowConstants.INSTANCE_APPROVED,
            WorkflowConstants.INSTANCE_REJECTED,
            WorkflowConstants.INSTANCE_WITHDRAWN,
            WorkflowConstants.INSTANCE_VOIDED
    );

    public List<Map<String, String>> getActionUsers(Long taskId, Long tenantId, Long userId) {
        WfTask task = wfTaskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getId, taskId)
                .eq(WfTask::getTenantId, tenantId)
                .eq(WfTask::getApproverId, userId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        if (task == null) {
            throw new BusinessException("WORKFLOW_TASK_NOT_AVAILABLE", "当前任务不可操作");
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenantId)
                        .eq(SysUser::getStatus, "ENABLE")
                        .ne(SysUser::getId, userId)
                        .orderByAsc(SysUser::getRealName, SysUser::getUsername))
                .stream()
                .map(candidate -> {
                    Map<String, String> option = new LinkedHashMap<>();
                    option.put("id", String.valueOf(candidate.getId()));
                    option.put("username", candidate.getUsername());
                    option.put("realName", candidate.getRealName());
                    option.put("status", candidate.getStatus());
                    return option;
                })
                .toList();
    }

    // ── 我的待办 ──

    public List<String> getVisibleBusinessTypes(Long tenantId, Long userId, String tab) {
        return switch (tab) {
            case "todo" -> visibleTodoBusinessTypes(tenantId, userId);
            case "done" -> visibleBusinessTypes(wfRecordMapper.selectList(
                    new LambdaQueryWrapper<WfRecord>()
                            .select(WfRecord::getBusinessType)
                            .eq(WfRecord::getTenantId, tenantId)
                            .eq(WfRecord::getOperatorId, userId)
                            .in(WfRecord::getActionType,
                                    WorkflowConstants.ACTION_APPROVE,
                                    WorkflowConstants.ACTION_REJECT,
                                    WorkflowConstants.ACTION_TRANSFER,
                                    WorkflowConstants.ACTION_ADD_SIGN)), WfRecord::getBusinessType);
            case "cc" -> visibleBusinessTypes(wfCcMapper.selectList(
                    new LambdaQueryWrapper<WfCc>()
                            .select(WfCc::getBusinessType)
                            .eq(WfCc::getTenantId, tenantId)
                            .eq(WfCc::getCcUserId, userId)), WfCc::getBusinessType);
            case "mine" -> visibleBusinessTypes(wfInstanceMapper.selectList(
                    new LambdaQueryWrapper<WfInstance>()
                            .select(WfInstance::getBusinessType)
                            .eq(WfInstance::getTenantId, tenantId)
                            .eq(WfInstance::getInitiatorId, userId)), WfInstance::getBusinessType);
            default -> throw new BusinessException("WORKFLOW_TAB_INVALID", "审批列表类型无效");
        };
    }

    private List<String> visibleTodoBusinessTypes(Long tenantId, Long userId) {
        List<WfTask> pendingTasks = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .select(WfTask::getInstanceId, WfTask::getBusinessType)
                        .eq(WfTask::getTenantId, tenantId)
                        .eq(WfTask::getApproverId, userId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        if (pendingTasks.isEmpty()) return List.of();
        Set<Long> pendingInstanceIds = pendingTasks.stream()
                .map(WfTask::getInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (pendingInstanceIds.isEmpty()) return List.of();
        Set<Long> runningInstanceIds = wfInstanceMapper.selectList(
                new LambdaQueryWrapper<WfInstance>()
                        .select(WfInstance::getId)
                        .eq(WfInstance::getTenantId, tenantId)
                        .eq(WfInstance::getInstanceStatus, WorkflowConstants.INSTANCE_RUNNING)
                        .in(WfInstance::getId, pendingInstanceIds))
                .stream().map(WfInstance::getId).collect(Collectors.toSet());
        return visibleBusinessTypes(pendingTasks.stream()
                .filter(task -> runningInstanceIds.contains(task.getInstanceId()))
                .toList(), WfTask::getBusinessType);
    }

    private <T> List<String> visibleBusinessTypes(List<T> rows, Function<T, String> getter) {
        return rows.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .filter(BUSINESS_TYPE_PATTERN.asMatchPredicate())
                .distinct()
                .sorted()
                .toList();
    }

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
        Map<Long, String> businessCodes = businessCodeResolver.resolveByInstanceId(
                tenantId, instanceMap.values());

        return page.convert(task -> {
            WfTaskVO vo = voAssembler.toTaskVO(task);
            vo.setBusinessType(task.getBusinessType());
            vo.setBusinessId(String.valueOf(task.getBusinessId()));
            vo.setBusinessCode(businessCodes.get(task.getInstanceId()));
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
        Map<Long, String> businessCodes = businessCodeResolver.resolveByInstanceId(
                tenantId, page.getRecords());

        return page.convert(instance -> {
            WfMyInstanceVO vo = new WfMyInstanceVO();
            vo.setInstanceId(String.valueOf(instance.getId()));
            vo.setBusinessType(instance.getBusinessType());
            if (instance.getBusinessId() != null) vo.setBusinessId(String.valueOf(instance.getBusinessId()));
            vo.setBusinessCode(businessCodes.get(instance.getId()));
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
        Map<Long, String> businessCodes = businessCodeResolver.resolveByInstanceId(
                tenantId, instanceMap.values());

        return page.convert(record -> {
            WfRecordVO vo = voAssembler.toRecordVO(record);
            vo.setBusinessCode(businessCodes.get(record.getInstanceId()));
            enrichRecordWithInstance(vo, instanceMap.get(record.getInstanceId()));
            return vo;
        });
    }

    public WfEfficiencyVO getMyEfficiency(Long tenantId, Long userId,
                                          String keyword, String businessType, String instanceStatus,
                                          LocalDateTime startTime, LocalDateTime endTime,
                                          int overdueHours, LocalDateTime now) {
        WfEfficiencyVO vo = new WfEfficiencyVO();
        int effectiveOverdueHours = Math.max(1, overdueHours);
        vo.setOverdueHours(effectiveOverdueHours);
        if (isUnsupportedBusinessType(businessType) || isUnsupportedInstanceStatus(instanceStatus)) {
            return vo;
        }

        String normalizedBusinessType = trimToNull(businessType);
        Set<Long> instanceIds = resolveInstanceIds(tenantId, keyword, normalizedBusinessType, instanceStatus);
        if (instanceIds != null && instanceIds.isEmpty()) {
            return vo;
        }

        Set<Long> runningInstanceIds = wfInstanceMapper.selectList(
                        new LambdaQueryWrapper<WfInstance>()
                                .select(WfInstance::getId)
                                .eq(WfInstance::getTenantId, tenantId)
                                .eq(WfInstance::getInstanceStatus, WorkflowConstants.INSTANCE_RUNNING))
                .stream().map(WfInstance::getId).collect(Collectors.toSet());

        LambdaQueryWrapper<WfTask> pendingWrapper = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTenantId, tenantId)
                .eq(WfTask::getApproverId, userId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING);
        if (runningInstanceIds.isEmpty()) {
            pendingWrapper.eq(WfTask::getId, -1L);
        } else {
            pendingWrapper.in(WfTask::getInstanceId, runningInstanceIds);
        }
        applyTaskFilters(pendingWrapper, normalizedBusinessType, instanceIds, startTime, endTime);
        vo.setPendingCount(wfTaskMapper.selectCount(pendingWrapper));

        LambdaQueryWrapper<WfTask> overdueWrapper = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTenantId, tenantId)
                .eq(WfTask::getApproverId, userId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                .lt(WfTask::getReceivedAt, now.minusHours(effectiveOverdueHours));
        if (runningInstanceIds.isEmpty()) {
            overdueWrapper.eq(WfTask::getId, -1L);
        } else {
            overdueWrapper.in(WfTask::getInstanceId, runningInstanceIds);
        }
        applyTaskFilters(overdueWrapper, normalizedBusinessType, instanceIds, startTime, endTime);
        vo.setOverduePendingCount(wfTaskMapper.selectCount(overdueWrapper));

        LambdaQueryWrapper<WfRecord> doneWrapper = buildDoneWrapper(userId, tenantId,
                normalizedBusinessType, instanceIds, startTime, endTime);
        vo.setDoneCount(wfRecordMapper.selectCount(doneWrapper));

        List<WfTask> handledTasks = wfTaskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTenantId, tenantId)
                .eq(WfTask::getApproverId, userId)
                .isNotNull(WfTask::getReceivedAt)
                .isNotNull(WfTask::getHandledAt)
                .in(WfTask::getTaskStatus,
                        WorkflowConstants.TASK_APPROVED,
                        WorkflowConstants.TASK_REJECTED,
                        WorkflowConstants.TASK_TRANSFERRED));
        List<WfTask> filteredHandledTasks = handledTasks.stream()
                .filter(task -> normalizedBusinessType == null || normalizedBusinessType.equals(task.getBusinessType()))
                .filter(task -> instanceIds == null || instanceIds.contains(task.getInstanceId()))
                .filter(task -> startTime == null || !task.getHandledAt().isBefore(startTime))
                .filter(task -> endTime == null || !task.getHandledAt().isAfter(endTime))
                .toList();
        long totalMinutes = filteredHandledTasks.stream()
                .mapToLong(task -> Math.max(0, Duration.between(task.getReceivedAt(), task.getHandledAt()).toMinutes()))
                .sum();
        vo.setHandledTaskCount(filteredHandledTasks.size());
        if (!filteredHandledTasks.isEmpty()) {
            vo.setAverageHandleMinutes(totalMinutes / filteredHandledTasks.size());
        }

        vo.setInstanceStatusCounts(countMyStartedByStatus(tenantId, userId,
                keyword, normalizedBusinessType, instanceStatus, startTime, endTime));
        return vo;
    }

    // ── 实例详情 ──

    public WfInstanceVO getInstanceDetail(Long tenantId, Long instanceId, Long currentUserId) {
        WfInstance instance = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getTenantId, tenantId)
                .eq(WfInstance::getId, instanceId));
        if (instance == null) return null;

        boolean taskParticipant = isTaskParticipant(tenantId, instanceId, currentUserId);
        if (!isAuthorized(instance, tenantId, instanceId, currentUserId, taskParticipant)) return null;
        if (!taskParticipant) requireProjectAccess(instance);

        WfInstanceVO vo = voAssembler.toInstanceVO(instance);
        vo.setBusinessCode(businessCodeResolver.resolveByInstanceId(
                tenantId, List.of(instance)).get(instance.getId()));
        vo.setBusinessDetails(costGovernanceDetails(instance));
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
        List<WfNodeVO> nodeVOs = buildNodeVOs(nodes, tasksByNode, vo.getBusinessCode());
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

    private Map<String, Object> costGovernanceDetails(WfInstance instance) {
        return switch (instance.getBusinessType()) {
            case com.cgcpms.workflow.WorkflowBusinessTypes.COST_RULE_PLAN -> {
                Map<String, Object> detail = detailWithRows(
                        """
                        SELECT version_code AS `versionCode`,version_name AS `versionName`,
                               effective_date AS `effectiveDate`,status,
                               validation_report AS `validationReport`
                        FROM cost_subject_mapping_version WHERE tenant_id=? AND id=?
                        """, "rules", """
                        SELECT r.rule_code AS `ruleCode`,r.source_type AS `sourceType`,
                               r.business_category AS `businessCategory`,p.project_code AS `projectCode`,
                               s.subject_code AS `targetSubjectCode`,s.subject_name AS `targetSubjectName`,
                               r.priority,r.effective_from AS `effectiveFrom`,r.effective_to AS `effectiveTo`
                        FROM cost_subject_assignment_rule r
                        JOIN cost_subject s ON s.tenant_id=r.tenant_id AND s.id=r.cost_subject_id
                        LEFT JOIN pm_project p ON p.tenant_id=r.tenant_id AND p.id=r.project_id
                        WHERE r.tenant_id=? AND r.mapping_version_id=? ORDER BY r.priority,r.id LIMIT 1000
                        """, instance);
                if (detail != null) {
                    detail.put("mappings", normalizeDetailRows(jdbcTemplate.queryForList("""
                            SELECT source.subject_code AS `sourceSubjectCode`,
                                   source.subject_name AS `sourceSubjectName`,
                                   item.target_group_code AS `targetGroupCode`,
                                   target.subject_code AS `targetSubjectCode`,
                                   target.subject_name AS `targetSubjectName`,
                                   item.mapping_reason AS `mappingReason`
                            FROM cost_subject_mapping_item item
                            JOIN cost_subject source ON source.tenant_id=item.tenant_id
                              AND source.id=item.source_subject_id
                            JOIN cost_subject target ON target.tenant_id=item.tenant_id
                              AND target.id=item.target_subject_id
                            WHERE item.tenant_id=? AND item.mapping_version_id=?
                            ORDER BY source.subject_code,item.id LIMIT 1000
                            """, instance.getTenantId(), instance.getBusinessId())));
                }
                yield detail;
            }
            case com.cgcpms.workflow.WorkflowBusinessTypes.COST_PROJECT_CONFIG -> detailWithRows(
                    """
                    SELECT r.request_code AS `requestCode`,p.project_code AS `projectCode`,
                           p.project_name AS `projectName`,r.project_status_snapshot AS `projectStatusSnapshot`,
                           r.reason,r.status,c.id AS `mainContractId`,c.contract_code AS `mainContractCode`,
                           c.current_amount AS `mainContractAmount`,c.approval_status AS `mainContractApprovalStatus`,
                           c.contract_status AS `mainContractStatus`,t.version_no AS `targetVersionNo`,
                           t.total_target_amount AS `targetAmount`
                    FROM cost_project_config_request r
                    JOIN pm_project p ON p.tenant_id=r.tenant_id AND p.id=r.project_id
                    LEFT JOIN ct_contract c ON c.tenant_id=r.tenant_id AND c.id=p.owner_contract_id
                      AND c.deleted_flag=0
                    LEFT JOIN cost_target t ON t.tenant_id=r.tenant_id AND t.project_id=r.project_id
                      AND t.status='ACTIVE' AND t.deleted_flag=0
                    WHERE r.tenant_id=? AND r.id=? ORDER BY t.id DESC LIMIT 1
                    """, "lines", """
                    SELECT s.subject_code AS `subjectCode`,s.subject_name AS `subjectName`,l.enabled,
                           l.effective_from AS `effectiveFrom`,l.effective_to AS `effectiveTo`,
                           l.impact_snapshot AS `impactSnapshot`
                    FROM cost_project_config_request_line l
                    JOIN cost_subject s ON s.tenant_id=l.tenant_id AND s.id=l.cost_subject_id
                    WHERE l.tenant_id=? AND l.request_id=? ORDER BY s.subject_code,l.id LIMIT 1000
                    """, instance);
            case com.cgcpms.workflow.WorkflowBusinessTypes.COST_RECALCULATION,
                 com.cgcpms.workflow.WorkflowBusinessTypes.COST_POST_CLOSE_ADJUSTMENT ->
                    recalculationApprovalDetail(instance);
            case com.cgcpms.workflow.WorkflowBusinessTypes.COST_REVERSAL -> reversalApprovalDetail(instance);
            default -> null;
        };
    }

    private Map<String, Object> recalculationApprovalDetail(WfInstance instance) {
        Map<String, Object> detail = detailOnly("""
                SELECT batch_code AS `batchCode`,batch_type AS `batchType`,scope_key AS `scopeKey`,
                       cutoff_at AS `cutoffAt`,original_fact_count AS `originalFactCount`,
                       changed_fact_count AS `changedFactCount`,unclassified_count AS `unclassifiedCount`,
                       original_total AS `originalTotal`,status,reason
                FROM cost_recalculation_batch WHERE tenant_id=? AND id=?
                """, instance);
        if (detail == null) return null;
        Integer groupCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT p.project_code,os.subject_code old_code,ns.subject_code new_code,l.difference_type
                  FROM cost_recalculation_line l
                  JOIN cost_item ci ON ci.tenant_id=l.tenant_id AND ci.id=l.original_cost_item_id
                  JOIN pm_project p ON p.tenant_id=ci.tenant_id AND p.id=ci.project_id
                  LEFT JOIN cost_subject os ON os.tenant_id=l.tenant_id AND os.id=l.old_cost_subject_id
                  LEFT JOIN cost_subject ns ON ns.tenant_id=l.tenant_id AND ns.id=l.new_cost_subject_id
                  WHERE l.tenant_id=? AND l.batch_id=? AND l.difference_type<>'UNCHANGED'
                  GROUP BY p.project_code,os.subject_code,ns.subject_code,l.difference_type
                ) grouped_difference
                """, Integer.class, instance.getTenantId(), instance.getBusinessId());
        List<Map<String, Object>> lines = normalizeDetailRows(jdbcTemplate.queryForList("""
                SELECT p.project_code AS `projectCode`,os.subject_code AS `oldSubjectCode`,os.subject_name AS `oldSubjectName`,
                       ns.subject_code AS `newSubjectCode`,ns.subject_name AS `newSubjectName`,
                       l.difference_type AS `differenceType`,COUNT(*) AS `factCount`,
                       SUM(l.amount) AS `amount`,SUM(l.tax_amount) AS `taxAmount`,
                       SUM(l.amount_without_tax) AS `amountWithoutTax`
                FROM cost_recalculation_line l
                JOIN cost_item ci ON ci.tenant_id=l.tenant_id AND ci.id=l.original_cost_item_id
                JOIN pm_project p ON p.tenant_id=ci.tenant_id AND p.id=ci.project_id
                LEFT JOIN cost_subject os ON os.tenant_id=l.tenant_id AND os.id=l.old_cost_subject_id
                LEFT JOIN cost_subject ns ON ns.tenant_id=l.tenant_id AND ns.id=l.new_cost_subject_id
                WHERE l.tenant_id=? AND l.batch_id=? AND l.difference_type<>'UNCHANGED'
                GROUP BY p.project_code,os.subject_code,os.subject_name,ns.subject_code,ns.subject_name,l.difference_type
                ORDER BY p.project_code,os.subject_code,ns.subject_code,l.difference_type LIMIT 1000
                """, instance.getTenantId(), instance.getBusinessId()));
        detail.put("lines", lines);
        detail.put("lineGroupCount", groupCount == null ? 0 : groupCount);
        detail.put("linesTruncated", groupCount != null && groupCount > lines.size());
        return detail;
    }

    private Map<String, Object> detailWithRows(String headerSql, String rowKey, String rowSql, WfInstance instance) {
        Map<String, Object> detail = detailOnly(headerSql, instance);
        if (detail == null) return null;
        detail.put(rowKey, normalizeDetailRows(
                jdbcTemplate.queryForList(rowSql, instance.getTenantId(), instance.getBusinessId())));
        return detail;
    }

    private Map<String, Object> detailOnly(String sql, WfInstance instance) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql, instance.getTenantId(), instance.getBusinessId());
        return rows.isEmpty() ? null : normalizeDetailMap(rows.getFirst());
    }

    private Map<String, Object> reversalApprovalDetail(WfInstance instance) {
        Map<String, Object> detail = detailOnly("""
                SELECT request_code AS `requestCode`,target_type AS `targetType`,
                       target_id AS `targetId`,project_id AS `projectId`,reason,status,
                       created_at AS `createdAt`
                FROM cost_reversal_request WHERE tenant_id=? AND id=?
                """, instance);
        if (detail == null) return null;
        Long targetId = ((Number) detail.get("targetId")).longValue();
        Map<String, Object> target = switch (String.valueOf(detail.get("targetType"))) {
            case "BID_TRANSFER" -> queryFirst("""
                    SELECT h.transfer_code AS `sourceCode`,p.project_code AS `projectCode`,
                           h.total_amount AS `originalAmount`
                    FROM bid_cost_target_transfer h
                    JOIN pm_project p ON p.tenant_id=h.tenant_id AND p.id=h.project_id
                    WHERE h.tenant_id=? AND h.id=?
                    """, instance.getTenantId(), targetId);
            case "FINANCE_ALLOCATION" -> queryFirst("""
                    SELECT b.batch_code AS `sourceCode`,b.accounting_period AS `accountingPeriod`,
                           b.source_amount AS `originalAmount`
                    FROM finance_cost_allocation_batch b
                    WHERE b.tenant_id=? AND b.id=?
                    """, instance.getTenantId(), targetId);
            case "RECALCULATION" -> queryFirst("""
                    SELECT b.batch_code AS `sourceCode`,
                           COALESCE(SUM(CASE WHEN l.difference_type='RECLASSIFY' THEN l.amount ELSE 0 END),0)
                             AS `originalAmount`,
                           SUM(CASE WHEN l.difference_type='RECLASSIFY' THEN 1 ELSE 0 END) AS `changedFactCount`
                    FROM cost_recalculation_batch b
                    LEFT JOIN cost_recalculation_line l ON l.tenant_id=b.tenant_id AND l.batch_id=b.id
                      AND l.difference_type='RECLASSIFY'
                    WHERE b.tenant_id=? AND b.id=?
                    GROUP BY b.batch_code
                    """, instance.getTenantId(), targetId);
            default -> null;
        };
        if (target != null) detail.putAll(target);
        String targetType = String.valueOf(detail.get("targetType"));
        if ("BID_TRANSFER".equals(targetType)) {
            Integer groupCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM (
                      SELECT l.source_subject_id,l.target_subject_id
                      FROM bid_cost_target_transfer_line l
                      WHERE l.tenant_id=? AND l.transfer_id=?
                      GROUP BY l.source_subject_id,l.target_subject_id
                    ) grouped
                    """, Integer.class, instance.getTenantId(), targetId);
            List<Map<String, Object>> lines = normalizeDetailRows(jdbcTemplate.queryForList("""
                    SELECT ss.subject_code AS `sourceSubjectCode`,ss.subject_name AS `sourceSubjectName`,
                           ts.subject_code AS `targetSubjectCode`,ts.subject_name AS `targetSubjectName`,
                           COUNT(*) AS `factCount`,COALESCE(SUM(l.amount),0) AS `amount`
                    FROM bid_cost_target_transfer_line l
                    LEFT JOIN cost_subject ss ON ss.tenant_id=l.tenant_id AND ss.id=l.source_subject_id
                    LEFT JOIN cost_subject ts ON ts.tenant_id=l.tenant_id AND ts.id=l.target_subject_id
                    WHERE l.tenant_id=? AND l.transfer_id=?
                    GROUP BY ss.subject_code,ss.subject_name,ts.subject_code,ts.subject_name
                    ORDER BY ss.subject_code,ts.subject_code LIMIT 1000
                    """, instance.getTenantId(), targetId));
            detail.put("lines", lines);
            detail.put("lineGroupCount", groupCount == null ? 0 : groupCount);
            detail.put("linesTruncated", groupCount != null && groupCount > lines.size());
        } else if ("FINANCE_ALLOCATION".equals(targetType)) {
            Integer groupCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM (
                      SELECT l.project_id,ci.cost_subject_id
                      FROM finance_cost_allocation_line l
                      JOIN cost_item ci ON ci.tenant_id=l.tenant_id AND ci.id=l.cost_item_id
                      WHERE l.tenant_id=? AND l.batch_id=?
                      GROUP BY l.project_id,ci.cost_subject_id
                    ) grouped
                    """, Integer.class, instance.getTenantId(), targetId);
            List<Map<String, Object>> lines = normalizeDetailRows(jdbcTemplate.queryForList("""
                    SELECT p.project_code AS `projectCode`,s.subject_code AS `subjectCode`,
                           s.subject_name AS `subjectName`,COUNT(*) AS `factCount`,
                           COALESCE(SUM(l.allocated_amount),0) AS `amount`
                    FROM finance_cost_allocation_line l
                    JOIN pm_project p ON p.tenant_id=l.tenant_id AND p.id=l.project_id
                    JOIN cost_item ci ON ci.tenant_id=l.tenant_id AND ci.id=l.cost_item_id
                    LEFT JOIN cost_subject s ON s.tenant_id=ci.tenant_id AND s.id=ci.cost_subject_id
                    WHERE l.tenant_id=? AND l.batch_id=?
                    GROUP BY p.project_code,s.subject_code,s.subject_name
                    ORDER BY p.project_code,s.subject_code LIMIT 1000
                    """, instance.getTenantId(), targetId));
            detail.put("lines", lines);
            detail.put("lineGroupCount", groupCount == null ? 0 : groupCount);
            detail.put("linesTruncated", groupCount != null && groupCount > lines.size());
        } else if ("RECALCULATION".equals(targetType)) {
            Integer groupCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM (
                      SELECT fact.project_id,l.old_cost_subject_id,l.new_cost_subject_id
                      FROM cost_recalculation_line l
                      JOIN cost_item fact ON fact.tenant_id=l.tenant_id AND fact.id=l.original_cost_item_id
                      WHERE l.tenant_id=? AND l.batch_id=? AND l.difference_type='RECLASSIFY'
                      GROUP BY fact.project_id,l.old_cost_subject_id,l.new_cost_subject_id
                    ) grouped
                    """, Integer.class, instance.getTenantId(), targetId);
            List<Map<String, Object>> lines = normalizeDetailRows(jdbcTemplate.queryForList("""
                    SELECT p.project_code AS `projectCode`,os.subject_code AS `oldSubjectCode`,
                           os.subject_name AS `oldSubjectName`,ns.subject_code AS `newSubjectCode`,
                           ns.subject_name AS `newSubjectName`,COUNT(*) AS `factCount`,
                           COALESCE(SUM(l.amount),0) AS `amount`,COALESCE(SUM(l.tax_amount),0) AS `taxAmount`,
                           COALESCE(SUM(l.amount_without_tax),0) AS `amountWithoutTax`
                    FROM cost_recalculation_line l
                    JOIN cost_item fact ON fact.tenant_id=l.tenant_id AND fact.id=l.original_cost_item_id
                    JOIN pm_project p ON p.tenant_id=fact.tenant_id AND p.id=fact.project_id
                    LEFT JOIN cost_subject os ON os.tenant_id=l.tenant_id AND os.id=l.old_cost_subject_id
                    LEFT JOIN cost_subject ns ON ns.tenant_id=l.tenant_id AND ns.id=l.new_cost_subject_id
                    WHERE l.tenant_id=? AND l.batch_id=? AND l.difference_type='RECLASSIFY'
                    GROUP BY p.project_code,os.subject_code,os.subject_name,ns.subject_code,ns.subject_name
                    ORDER BY p.project_code,os.subject_code,ns.subject_code LIMIT 1000
                    """, instance.getTenantId(), targetId));
            detail.put("lines", lines);
            detail.put("lineGroupCount", groupCount == null ? 0 : groupCount);
            detail.put("linesTruncated", groupCount != null && groupCount > lines.size());
        }
        detail.put("reversalAmount", target == null ? null : target.get("originalAmount"));
        return detail;
    }

    private Map<String, Object> queryFirst(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? null : normalizeDetailMap(rows.getFirst());
    }

    private static List<Map<String, Object>> normalizeDetailRows(List<Map<String, Object>> rows) {
        return rows.stream().map(WorkflowQueryService::normalizeDetailMap).toList();
    }

    private static Map<String, Object> normalizeDetailMap(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> normalized.put(
                COST_DETAIL_KEY_CASE.getOrDefault(key.toLowerCase(Locale.ROOT), key), value));
        return normalized;
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
        Map<Long, String> businessCodes = businessCodeResolver.resolveByInstanceId(
                tenantId, instanceMap.values());

        return page.convert(cc -> {
            WfCcVO vo = voAssembler.toCcVO(cc);
            vo.setBusinessCode(businessCodes.get(cc.getInstanceId()));
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
        Set<Long> result = wfInstanceMapper.selectList(wrapper).stream()
                .map(WfInstance::getId)
                .collect(Collectors.toSet());
        if (normalizedKeyword != null) {
            result.addAll(businessCodeResolver.findInstanceIds(
                    tenantId, normalizedKeyword, normalizedBusinessType, normalizedStatus));
        }
        return result;
    }

    private boolean isUnsupportedBusinessType(String businessType) {
        String normalized = trimToNull(businessType);
        return normalized != null && !BUSINESS_TYPE_PATTERN.matcher(normalized).matches();
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

    private void applyTaskFilters(LambdaQueryWrapper<WfTask> wrapper, String businessType,
                                  Set<Long> instanceIds, LocalDateTime startTime, LocalDateTime endTime) {
        if (businessType != null) {
            wrapper.eq(WfTask::getBusinessType, businessType);
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
    }

    private LambdaQueryWrapper<WfRecord> buildDoneWrapper(Long userId, Long tenantId, String businessType,
                                                          Set<Long> instanceIds,
                                                          LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<WfRecord> wrapper = new LambdaQueryWrapper<WfRecord>()
                .eq(WfRecord::getTenantId, tenantId)
                .eq(WfRecord::getOperatorId, userId)
                .in(WfRecord::getActionType,
                        WorkflowConstants.ACTION_APPROVE,
                        WorkflowConstants.ACTION_REJECT,
                        WorkflowConstants.ACTION_TRANSFER,
                        WorkflowConstants.ACTION_ADD_SIGN);
        if (businessType != null) {
            wrapper.eq(WfRecord::getBusinessType, businessType);
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
        return wrapper;
    }

    private Map<String, Long> countMyStartedByStatus(Long tenantId, Long userId, String keyword,
                                                     String businessType, String instanceStatus, LocalDateTime startTime,
                                                     LocalDateTime endTime) {
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = trimToNull(instanceStatus);
        LambdaQueryWrapper<WfInstance> wrapper = new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getTenantId, tenantId)
                .eq(WfInstance::getInitiatorId, userId);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(WfInstance::getTitle, normalizedKeyword)
                    .or()
                    .like(WfInstance::getBusinessSummary, normalizedKeyword));
        }
        if (businessType != null) {
            wrapper.eq(WfInstance::getBusinessType, businessType);
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
        return wfInstanceMapper.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(WfInstance::getInstanceStatus, LinkedHashMap::new, Collectors.counting()));
    }

    private boolean isAuthorized(WfInstance instance, Long tenantId, Long instanceId,
                                 Long currentUserId, boolean taskParticipant) {
        if (instance.getInitiatorId().equals(currentUserId)) return true;
        if (taskParticipant) return true;
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

    private boolean isTaskParticipant(Long tenantId, Long instanceId, Long currentUserId) {
        return wfTaskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTenantId, tenantId)
                .eq(WfTask::getInstanceId, instanceId)
                .eq(WfTask::getApproverId, currentUserId)) > 0;
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
                                        Map<Long, List<WfTask>> tasksByNode,
                                        String businessCode) {
        List<WfNodeVO> result = new ArrayList<>();
        for (WfNodeInstance n : nodes) {
            WfNodeVO nvo = voAssembler.toNodeVO(n);
            List<WfTask> tasks = tasksByNode.getOrDefault(n.getId(), Collections.emptyList());
            nvo.setTasks(tasks.stream().map(task -> {
                WfTaskVO taskVO = voAssembler.toTaskVO(task);
                taskVO.setBusinessCode(businessCode);
                return taskVO;
            }).collect(Collectors.toList()));
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
