package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.notification.service.NotificationService;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.role.SystemRoleContract;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.WorkflowSecurityPolicy;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowBusinessHandlerRegistry;
import com.cgcpms.workflow.handler.WorkflowContext;
import com.cgcpms.workflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Internal shared helpers for workflow sub-services.
 * Package-private -- not for external consumption.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class WorkflowCoreService {

    private static final ObjectMapper POLICY_MAPPER = new ObjectMapper();

    final WfTemplateMapper wfTemplateMapper;
    final WfTemplateNodeMapper wfTemplateNodeMapper;
    final WfInstanceMapper wfInstanceMapper;
    final WfNodeInstanceMapper wfNodeInstanceMapper;
    final WfTaskMapper wfTaskMapper;
    final WfRecordMapper wfRecordMapper;
    final WfIdempotencyMapper wfIdempotencyMapper;
    final SysUserMapper sysUserMapper;
    final WorkflowBusinessHandlerRegistry handlerRegistry;
    final NotificationService notificationService;
    final ApproverResolver approverResolver;
    final JdbcTemplate jdbcTemplate;

    void requireCurrentTenant(Long targetTenantId) {
        if (!Objects.equals(targetTenantId, UserContext.getCurrentTenantId())) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "资源不存在");
        }
    }

    // ── Template lookup ──
    WfTemplate findTemplate(String businessType, Long tenantId, java.math.BigDecimal amount,
                            Long businessId, Long contractId) {
        WfTemplate template = queryRoutedTemplate(businessType, tenantId, amount, businessId, contractId);
        if (template == null && tenantId != null && tenantId != 0L)
            template = queryRoutedTemplate(businessType, 0L, amount, businessId, contractId);
        if (template != null) return template;
        template = queryTemplate(businessType, tenantId, amount);
        if (template == null && tenantId != null && tenantId != 0L)
            template = queryTemplate(businessType, 0L, amount);
        if (template == null)
            throw new BusinessException("TEMPLATE_NOT_FOUND", "未找到业务类型 [" + businessType + "] 的审批模板");
        return template;
    }

    private WfTemplate queryTemplate(String businessType, Long tenantId, java.math.BigDecimal amount) {
        LambdaQueryWrapper<WfTemplate> wrapper = new LambdaQueryWrapper<WfTemplate>()
                .eq(WfTemplate::getBusinessType, businessType)
                .eq(WfTemplate::getTenantId, tenantId).eq(WfTemplate::getEnabled, 1);
        if (amount != null) {
            wrapper.and(w -> w.isNull(WfTemplate::getAmountMin).or().le(WfTemplate::getAmountMin, amount))
                    .and(w -> w.isNull(WfTemplate::getAmountMax).or().ge(WfTemplate::getAmountMax, amount));
        }
        List<WfTemplate> templates = wfTemplateMapper.selectList(wrapper);
        if (templates.isEmpty()) return null;
        templates.sort((a,b)->{
            int min=compareMinDesc(a.getAmountMin(),b.getAmountMin());
            if(min!=0)return min;
            int max=compareMaxAsc(a.getAmountMax(),b.getAmountMax());
            if(max!=0)return max;
            return Comparator.nullsLast(LocalDateTime::compareTo).compare(a.getCreatedAt(),b.getCreatedAt());
        });
        if(templates.size()>1&&Objects.equals(templates.get(0).getAmountMin(),templates.get(1).getAmountMin())
                &&Objects.equals(templates.get(0).getAmountMax(),templates.get(1).getAmountMax())){
            throw new BusinessException("TEMPLATE_AMBIGUOUS", "存在相同业务类型和金额范围的多个审批模板");
        }
        return templates.get(0);
    }

    private WfTemplate queryRoutedTemplate(String businessType,Long ruleTenantId,
                                           java.math.BigDecimal amount,Long businessId,Long contractId){
        String contractType=null,expenseCategory=null;
        if(contractId!=null){
            List<String> values=jdbcTemplate.query("SELECT contract_type FROM ct_contract WHERE id=? AND tenant_id=? AND deleted_flag=0",
                    (rs,row)->rs.getString(1),contractId,UserContext.getCurrentTenantId());
            if(!values.isEmpty())contractType=values.getFirst();
        }
        if(businessId!=null&&WorkflowBusinessTypes.EXPENSE.equals(businessType)){
            List<String> values=jdbcTemplate.query("SELECT expense_category FROM expense_application WHERE id=? AND tenant_id=? AND deleted_flag=0",
                    (rs,row)->rs.getString(1),businessId,UserContext.getCurrentTenantId());
            if(!values.isEmpty())expenseCategory=values.getFirst();
        }else if(businessId!=null&&WorkflowBusinessTypes.PAY_REQUEST.equals(businessType)){
            List<String> values=jdbcTemplate.query("SELECT expense_category FROM pay_application WHERE id=? AND tenant_id=? AND deleted_flag=0",
                    (rs,row)->rs.getString(1),businessId,UserContext.getCurrentTenantId());
            if(!values.isEmpty())expenseCategory=values.getFirst();
        }
        List<java.util.Map<String,Object>> rules=jdbcTemplate.queryForList("""
                SELECT id,workflow_template_id,priority FROM approval_routing_rule
                WHERE tenant_id=? AND business_type=? AND enabled_flag=1
                  AND (min_amount IS NULL OR min_amount<=?) AND (max_amount IS NULL OR max_amount>=?)
                  AND (contract_type IS NULL OR contract_type=?)
                  AND (expense_category IS NULL OR expense_category=?)
                ORDER BY priority,id
                """,ruleTenantId,businessType,amount,amount,contractType,expenseCategory);
        if(rules.isEmpty())return null;
        int priority=((Number)rules.getFirst().get("priority")).intValue();
        List<java.util.Map<String,Object>> best=rules.stream()
                .filter(r->((Number)r.get("priority")).intValue()==priority).toList();
        List<Long> templateIds=best.stream().map(r->((Number)r.get("workflow_template_id")).longValue()).distinct().toList();
        if(templateIds.size()>1)throw new BusinessException("APPROVAL_ROUTING_AMBIGUOUS","存在同优先级且指向不同模板的审批路由");
        WfTemplate template=wfTemplateMapper.selectOne(new LambdaQueryWrapper<WfTemplate>()
                .eq(WfTemplate::getId,templateIds.getFirst())
                .eq(WfTemplate::getTenantId,ruleTenantId)
                .eq(WfTemplate::getEnabled,1));
        if(template==null||!businessType.equals(template.getBusinessType()))
            throw new BusinessException("APPROVAL_ROUTING_TEMPLATE_INVALID","审批路由指向了不可用或业务类型不一致的模板");
        return template;
    }

    private int compareMinDesc(java.math.BigDecimal a,java.math.BigDecimal b){
        if(a==null&&b==null)return 0;if(a==null)return 1;if(b==null)return -1;return b.compareTo(a);
    }
    private int compareMaxAsc(java.math.BigDecimal a,java.math.BigDecimal b){
        if(a==null&&b==null)return 0;if(a==null)return 1;if(b==null)return -1;return a.compareTo(b);
    }

    List<WfTemplateNode> findTemplateNodes(Long templateId) {
        List<WfTemplateNode> nodes = wfTemplateNodeMapper.selectList(
                new LambdaQueryWrapper<WfTemplateNode>()
                        .eq(WfTemplateNode::getTemplateId, templateId)
                        .orderByAsc(WfTemplateNode::getNodeOrder));
        if (nodes.isEmpty()) throw new BusinessException("TEMPLATE_NODES_EMPTY", "审批模板未配置节点");
        return nodes;
    }

    // ── Node activation ──
    void activateNode(WfNodeInstance node, Long userId, String username, Long tenantId) {
        node.setNodeStatus(WorkflowConstants.NODE_ACTIVE);
        node.setStartedAt(LocalDateTime.now());
        wfNodeInstanceMapper.updateById(node);
        createTasksForNode(node, userId, username, tenantId);
    }

    private void createTasksForNode(WfNodeInstance node, Long userId, String username, Long tenantId) {
        WfInstance instance = wfInstanceMapper.selectById(node.getInstanceId());
        List<Long> approverIds = eligibleApproverIds(node, instance);
        if (approverIds.isEmpty()) {
            throw new BusinessException("WORKFLOW_APPROVER_SEPARATION_UNSATISFIED",
                    "审批节点无满足发起人隔离和审批次数限制的候选人");
        }
        for (Long approverId : approverIds) {
            SysUser approverUser = sysUserMapper.selectById(approverId);
            String approverName = approverUser != null
                    ? (approverUser.getRealName() != null ? approverUser.getRealName() : approverUser.getUsername())
                    : "";
            WfTask task = new WfTask();
            task.setTenantId(tenantId);
            task.setInstanceId(node.getInstanceId());
            task.setNodeInstanceId(node.getId());
            task.setBusinessType(instance.getBusinessType());
            task.setBusinessId(instance.getBusinessId());
            task.setApproverId(approverId);
            task.setApproverName(approverName);
            task.setTaskStatus(WorkflowConstants.TASK_PENDING);
            task.setRoundNo(node.getRoundNo());
            task.setReceivedAt(LocalDateTime.now());
            wfTaskMapper.insert(task);
        }
    }

    void requireEligibleApprover(WfNodeInstance node, WfInstance instance, Long userId) {
        if (!eligibleApproverIds(node, instance).contains(userId)) {
            throw new BusinessException("WORKFLOW_TARGET_NOT_ELIGIBLE", "目标用户不符合当前节点审批人快照与安全策略");
        }
    }

    private List<Long> eligibleApproverIds(WfNodeInstance node, WfInstance instance) {
        WorkflowSecurityPolicy policy = WorkflowSecurityPolicy.parseOrLegacy(
                POLICY_MAPPER, instance.getSecurityPolicyJson());
        boolean requireAllFinanceProjects = requiresAllFinanceProjects(node, instance);
        return approverResolver.resolve(
                        node.getApproverConfig(), instance.getTenantId(), instance.getProjectId(), policy).stream()
                .filter(approverId -> !policy.preventInitiatorApproval()
                        || !Objects.equals(approverId, instance.getInitiatorId()))
                .filter(approverId -> approvedCount(instance.getTenantId(), instance.getId(), approverId, node.getRoundNo())
                        < policy.maxApprovalsPerUser())
                .filter(approverId -> !requireAllFinanceProjects
                        || belongsToAllFinanceProjects(instance, approverId))
                .distinct()
                .toList();
    }

    private boolean requiresAllFinanceProjects(WfNodeInstance node, WfInstance instance) {
        if (!WorkflowBusinessTypes.FINANCE_COST_ALLOCATION.equals(instance.getBusinessType())) return false;
        try {
            JsonNode config = POLICY_MAPPER.readTree(node.getApproverConfig());
            String type = config.path("type").asText("");
            if ("PROJECT_ROLE".equalsIgnoreCase(type)) return true;
            if (!"ROLE".equalsIgnoreCase(type)) return false;
            String roleCode = config.path("roleCode").asText(null);
            if (roleCode == null && config.has("roleId")) {
                List<String> codes = jdbcTemplate.queryForList(
                        "SELECT role_code FROM sys_role WHERE tenant_id=? AND id=? AND deleted_flag=0",
                        String.class, instance.getTenantId(), config.get("roleId").asLong());
                roleCode = codes.isEmpty() ? null : codes.getFirst();
            }
            return SystemRoleContract.PROJECT_SCOPED_ROLE_CODES.contains(
                    SystemRoleContract.canonicalRoleCode(roleCode));
        } catch (Exception e) {
            throw new BusinessException("INVALID_APPROVER_CONFIG", "审批人配置JSON格式无效");
        }
    }

    private boolean belongsToAllFinanceProjects(WfInstance instance, Long userId) {
        List<Long> projects = jdbcTemplate.queryForList("""
                SELECT project_id FROM finance_cost_allocation_request_line
                WHERE tenant_id=? AND request_id=?
                """, Long.class, instance.getTenantId(), instance.getBusinessId());
        List<Long> memberships = jdbcTemplate.queryForList("""
                SELECT m.project_id
                FROM finance_cost_allocation_request_line l
                JOIN pm_project_member m ON m.tenant_id=l.tenant_id AND m.project_id=l.project_id
                 AND m.user_id=? AND m.status='ACTIVE' AND m.deleted_flag=0
                WHERE l.tenant_id=? AND l.request_id=?
                FOR UPDATE
                """, Long.class, userId, instance.getTenantId(), instance.getBusinessId());
        return !projects.isEmpty() && Set.copyOf(projects).equals(Set.copyOf(memberships));
    }

    void validateApproverPlan(List<WfTemplateNode> nodes, Long tenantId, Long projectId,
                              Long initiatorId, WorkflowSecurityPolicy policy) {
        validateCandidatePlan(nodes.stream().map(WfTemplateNode::getApproverConfig).toList(),
                tenantId, projectId, initiatorId, policy);
    }

    void validateApproverSnapshots(List<WfNodeInstance> nodes, Long tenantId, Long projectId,
                                   Long initiatorId, WorkflowSecurityPolicy policy) {
        validateCandidatePlan(nodes.stream().map(WfNodeInstance::getApproverConfig).toList(),
                tenantId, projectId, initiatorId, policy);
    }

    private void validateCandidatePlan(List<String> approverConfigs, Long tenantId, Long projectId,
                                       Long initiatorId, WorkflowSecurityPolicy policy) {
        List<List<Long>> candidatesByNode = new ArrayList<>();
        for (String config : approverConfigs) {
            List<Long> candidates = approverResolver.resolve(config, tenantId, projectId, policy).stream()
                    .filter(candidate -> !policy.preventInitiatorApproval()
                            || !Objects.equals(candidate, initiatorId))
                    .distinct()
                    .toList();
            if (candidates.isEmpty()) {
                throw new BusinessException("WORKFLOW_APPROVER_SEPARATION_UNSATISFIED",
                        "提交时无法解析满足安全策略的审批人");
            }
            candidatesByNode.add(candidates);
        }
        if (!assignCandidate(0, candidatesByNode, new HashMap<>(), policy.maxApprovalsPerUser())) {
            throw new BusinessException("WORKFLOW_APPROVER_SEPARATION_UNSATISFIED",
                    "审批人数量不足以满足每人审批次数限制");
        }
    }

    private boolean assignCandidate(int index, List<List<Long>> candidatesByNode,
                                    Map<Long, Integer> assigned, int maximum) {
        if (index == candidatesByNode.size()) return true;
        for (Long candidate : candidatesByNode.get(index)) {
            int used = assigned.getOrDefault(candidate, 0);
            if (used >= maximum) continue;
            assigned.put(candidate, used + 1);
            if (assignCandidate(index + 1, candidatesByNode, assigned, maximum)) return true;
            if (used == 0) assigned.remove(candidate); else assigned.put(candidate, used);
        }
        return false;
    }

    long approvedCount(Long tenantId, Long instanceId, Long userId, Integer roundNo) {
        return wfTaskMapper.selectApprovedIdsForUpdate(tenantId, instanceId, userId, roundNo).size();
    }

    // ── Node completion ──
    boolean isNodeComplete(Long tenantId, Long nodeInstanceId, String approveMode) {
        long pendingCount = wfTaskMapper.selectPendingIdsForUpdate(tenantId, nodeInstanceId).size();
        return WorkflowConstants.MODE_OR_SIGN.equals(approveMode) || pendingCount == 0;
    }

    void completeNode(Long nodeInstanceId) {
        WfNodeInstance node = wfNodeInstanceMapper.selectById(nodeInstanceId);
        if (node != null) {
            node.setNodeStatus(WorkflowConstants.NODE_COMPLETED);
            node.setEndedAt(LocalDateTime.now());
            wfNodeInstanceMapper.updateById(node);
        }
    }

    // ── Node traversal ──
    WfNodeInstance findNextWaitingNode(Long instanceId, int roundNo) {
        Page<WfNodeInstance> page = new Page<>(0, 1);
        Page<WfNodeInstance> result = wfNodeInstanceMapper.selectPage(page,
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, instanceId)
                        .eq(WfNodeInstance::getRoundNo, roundNo)
                        .eq(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_WAITING)
                        .orderByAsc(WfNodeInstance::getNodeOrder));
        return result.getRecords().isEmpty() ? null : result.getRecords().get(0);
    }

    WfNodeInstance findRejectedOrLastNode(Long instanceId) {
        Page<WfNodeInstance> page = new Page<>(0, 1);
        Page<WfNodeInstance> result = wfNodeInstanceMapper.selectPage(page,
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, instanceId)
                        .and(w -> w.eq(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_REJECTED)
                                .or().eq(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_ACTIVE))
                        .orderByDesc(WfNodeInstance::getNodeOrder));
        return result.getRecords().isEmpty() ? null : result.getRecords().get(0);
    }

    // ── Task cancellation ──
    public void cancelOrSignPendingTasks(Long nodeInstanceId, Long excludeTaskId) {
        cancelPendingTasksInNode(nodeInstanceId, excludeTaskId);
    }

    void cancelPendingTasksInNode(Long nodeInstanceId, Long excludeTaskId) {
        LambdaUpdateWrapper<WfTask> wrapper = new LambdaUpdateWrapper<WfTask>()
                .eq(WfTask::getNodeInstanceId, nodeInstanceId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING);
        if (excludeTaskId != null) wrapper.ne(WfTask::getId, excludeTaskId);
        wfTaskMapper.update(null, wrapper
                .set(WfTask::getTaskStatus, WorkflowConstants.TASK_CANCELLED)
                .set(WfTask::getHandledAt, LocalDateTime.now())
                .setSql("task_version = task_version + 1"));
    }

    void cancelAllPendingTasks(Long instanceId) {
        wfTaskMapper.update(null, new LambdaUpdateWrapper<WfTask>()
                .eq(WfTask::getInstanceId, instanceId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                .set(WfTask::getTaskStatus, WorkflowConstants.TASK_CANCELLED)
                .set(WfTask::getHandledAt, LocalDateTime.now())
                .setSql("task_version = task_version + 1"));
    }

    void resetActiveNodes(Long instanceId) {
        List<WfNodeInstance> nodes = wfNodeInstanceMapper.selectList(
                new LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, instanceId)
                        .eq(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_ACTIVE));
        if (!nodes.isEmpty()) {
            List<Long> nodeIds = nodes.stream().map(WfNodeInstance::getId).collect(Collectors.toList());
            wfNodeInstanceMapper.update(null, new LambdaUpdateWrapper<WfNodeInstance>()
                    .in(WfNodeInstance::getId, nodeIds)
                    .set(WfNodeInstance::getNodeStatus, WorkflowConstants.NODE_WAITING)
                    .set(WfNodeInstance::getEndedAt, LocalDateTime.now()));
        }
    }

    // ── Record writing ──
    void writeRecord(Long tenantIdOverride, String businessTypeOverride, Long businessIdOverride,
                     Long instanceId, Long nodeInstanceId, Long taskId,
                     int roundNo, String nodeCode, String nodeName,
                     String actionType, String actionName,
                     Long operatorId, String operatorName, String comment) {
        Long tenantId = tenantIdOverride != null ? tenantIdOverride : 0L;
        String businessType = businessTypeOverride;
        Long businessId = businessIdOverride;
        try {
            WfInstance inst = wfInstanceMapper.selectById(instanceId);
            if (inst != null) {
                if (tenantIdOverride == null) tenantId = inst.getTenantId();
                if (businessType == null) businessType = inst.getBusinessType();
                if (businessId == null) businessId = inst.getBusinessId();
            }
        } catch (Exception e) {
            log.error("Failed to load instance for record writing, instanceId={}", instanceId, e);
            return;
        }
        WfRecord record = new WfRecord();
        record.setTenantId(tenantId);
        record.setBusinessType(businessType);
        record.setBusinessId(businessId);
        record.setInstanceId(instanceId);
        record.setNodeInstanceId(nodeInstanceId);
        record.setTaskId(taskId);
        record.setRoundNo(roundNo);
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

    // ── Idempotency ──
    void checkIdempotency(Long tenantId, Long userId, String idempotencyKey) {
        WfIdempotency idem = new WfIdempotency();
        idem.setTenantId(tenantId != null ? tenantId : 0L);
        idem.setUserId(userId);
        idem.setIdempotencyKey(idempotencyKey);
        idem.setCreatedAt(LocalDateTime.now());
        idem.setExpiredAt(LocalDateTime.now().plusHours(WorkflowConstants.IDEMPOTENCY_EXPIRE_HOURS));
        try {
            wfIdempotencyMapper.insert(idem);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_REQUEST", "重复请求，操作已执行过，请勿重复提交");
        }
    }

    // ── Handler notification ──
    void notifyHandler(String businessType, WfInstance instance,
                       String actionType, String operatorName, String comment) {
        if (!handlerRegistry.hasHandler(businessType)) {
            log.debug("No business handler registered for type: {}", businessType);
            return;
        }
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);
        ctx.setActionType(actionType);
        ctx.setOperatorName(operatorName);
        ctx.setComment(comment);
        var handler = handlerRegistry.get(businessType);
        if (handler.isCritical()) {
            dispatchToHandler(handler, ctx, instance, actionType);
        } else {
            try { dispatchToHandler(handler, ctx, instance, actionType); }
            catch (Exception e) { log.error("Business handler error for type={}, action={}", businessType, actionType, e); }
        }
    }

    private void dispatchToHandler(WorkflowBusinessHandler handler, WorkflowContext ctx,
                                   WfInstance instance, String actionType) {
        switch (actionType) {
            case WorkflowConstants.ACTION_SUBMIT -> handler.beforeSubmit(ctx);
            case WorkflowConstants.ACTION_APPROVE -> {
                if (WorkflowConstants.INSTANCE_APPROVED.equals(instance.getInstanceStatus()))
                    handler.onApproved(ctx);
                else
                    handler.onRunning(ctx);
            }
            case WorkflowConstants.ACTION_REJECT -> handler.onRejected(ctx);
            case WorkflowConstants.ACTION_WITHDRAW -> handler.onWithdrawn(ctx);
        }
    }
}
