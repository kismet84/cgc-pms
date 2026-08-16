package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.service.CostSubjectV2Service.ProjectConfigCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.ProjectConfigLine;
import com.cgcpms.cost.service.CostSubjectV2Service.RecalculationCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.ReversalCommand;
import com.cgcpms.cost.strategy.CostSubjectResolver;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CostGovernanceOperations extends CostSubjectV2Support {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> POSTED_ADJUSTMENT_SOURCES = List.of(
            "COST_RECALCULATION_NEGATIVE", "COST_RECALCULATION_REVERSAL");

    private final CostSubjectResolver resolver;
    private final AccountingPeriodGuard accountingPeriodGuard;
    private final ObjectProvider<WorkflowEngine> workflowEngineProvider;

    CostGovernanceOperations(JdbcTemplate jdbc, ProjectAccessChecker projectAccessChecker,
                             CostSubjectResolver resolver,
                             AccountingPeriodGuard accountingPeriodGuard,
                             ObjectProvider<WorkflowEngine> workflowEngineProvider) {
        super(jdbc, projectAccessChecker);
        this.resolver = resolver;
        this.accountingPeriodGuard = accountingPeriodGuard;
        this.workflowEngineProvider = workflowEngineProvider;
    }

    Map<String, Object> formOptions() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Long> projectIds = projectAccessChecker.accessibleProjectIds();
        boolean allScope = projectAccessChecker.hasAllScope();
        if (projectIds.isEmpty() && !allScope) {
            result.put("projects", List.of());
        } else {
            List<Object> projectArguments = new ArrayList<>();
            projectArguments.add(tenantId());
            if (!allScope) projectArguments.addAll(projectIds);
            result.put("projects", jdbc.queryForList("""
                SELECT id,project_code projectCode,project_name projectName,status projectStatus
                FROM pm_project WHERE tenant_id=? AND deleted_flag=0 %s
                ORDER BY project_code,id LIMIT 200
                """.formatted(allScope ? "" : "AND id IN (" + placeholders(projectIds) + ")"),
                    projectArguments.toArray()));
        }
        result.put("costSubjects", jdbc.queryForList("""
                SELECT s.id,s.subject_code subjectCode,s.subject_name subjectName,s.subject_type subjectType,s.status,
                       CASE WHEN EXISTS (SELECT 1 FROM overhead_allocation_rule r
                         WHERE r.tenant_id=s.tenant_id AND r.cost_subject_id=s.id
                           AND r.status='DISABLE' AND r.deleted_flag=0)
                         THEN 'DISABLE' ELSE NULL END overheadRuleStatus
                FROM cost_subject s
                WHERE s.tenant_id=? AND s.deleted_flag=0 AND s.account_category='COST'
                  AND NOT EXISTS (SELECT 1 FROM cost_subject c WHERE c.tenant_id=s.tenant_id
                                  AND c.parent_id=s.id AND c.deleted_flag=0)
                ORDER BY s.subject_code,s.id LIMIT 500
                """, tenantId()));
        result.put("rulePlans", jdbc.queryForList("""
                SELECT id,version_code versionCode,version_name versionName,status,effective_date effectiveDate
                FROM cost_subject_mapping_version WHERE tenant_id=?
                ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'VALIDATED' THEN 1 ELSE 2 END,created_at DESC
                LIMIT 100
                """, tenantId()));
        if (projectIds.isEmpty() && !allScope) {
            result.put("pendingClassifications", List.of());
            result.put("bidCosts", List.of());
            result.put("targetVersions", List.of());
            result.put("financeSources", List.of());
        } else {
            List<Object> classificationArguments = new ArrayList<>();
            classificationArguments.add(tenantId());
            if (!allScope) classificationArguments.addAll(projectIds);
            classificationArguments.add(tenantId());
            if (!allScope) classificationArguments.addAll(projectIds);
            result.put("pendingClassifications", jdbc.queryForList("""
                SELECT c.id caseId,NULL snapshotId,c.source_type sourceType,c.source_id sourceId,
                       c.source_item_id sourceItemId,c.project_id projectId,NULL matchedCostSubjectId,
                       original.subject_code matchedSubjectCode,original.subject_name matchedSubjectName,
                       c.error_code errorCode,c.error_message errorMessage,c.created_at createdAt
                FROM cost_unclassified_case c
                LEFT JOIN cost_subject original ON original.tenant_id=c.tenant_id
                  AND original.id=c.original_cost_subject_id
                WHERE c.tenant_id=? AND c.status='OPEN' %s
                UNION ALL
                SELECT NULL caseId,s.id snapshotId,s.source_type sourceType,s.source_id sourceId,
                       s.source_item_id sourceItemId,s.project_id projectId,s.matched_cost_subject_id matchedCostSubjectId,
                       cs.subject_code matchedSubjectCode,cs.subject_name matchedSubjectName,
                       NULL errorCode,NULL errorMessage,s.created_at createdAt
                FROM cost_classification_snapshot s
                JOIN cost_subject cs ON cs.tenant_id=s.tenant_id AND cs.id=s.matched_cost_subject_id
                WHERE s.tenant_id=? AND s.status='PENDING' %s
                ORDER BY createdAt DESC LIMIT 200
                """.formatted(allScope ? "" : "AND c.project_id IN (" + placeholders(projectIds) + ")",
                    allScope ? "" : "AND s.project_id IN (" + placeholders(projectIds) + ")"),
                    classificationArguments.toArray()));

            List<Object> scopedArguments = new ArrayList<>();
            scopedArguments.add(tenantId());
            if (!allScope) scopedArguments.addAll(projectIds);
            String projectClause = allScope ? "" : " AND p.id IN (" + placeholders(projectIds) + ")";
            result.put("bidCosts", jdbc.queryForList("""
                    SELECT b.id,b.bid_code bidCode,b.bid_project_name bidProjectName,b.project_id projectId,
                           p.project_code projectCode,p.project_name projectName
                    FROM bid_cost b JOIN pm_project p ON p.tenant_id=b.tenant_id AND p.id=b.project_id
                    WHERE b.tenant_id=? AND b.deleted_flag=0 AND b.bid_status='WON'
                    """ + projectClause + " ORDER BY b.updated_at DESC,b.id DESC LIMIT 200", scopedArguments.toArray()));
            result.put("targetVersions", jdbc.queryForList("""
                    SELECT t.id,t.project_id projectId,p.project_code projectCode,p.project_name projectName,
                           t.version_no versionNo,t.version_name versionName,t.total_target_amount totalTargetAmount,
                           t.status,t.approval_status approvalStatus
                    FROM cost_target t JOIN pm_project p ON p.tenant_id=t.tenant_id AND p.id=t.project_id
                    WHERE t.tenant_id=? AND t.deleted_flag=0
                      AND t.approval_status IN ('DRAFT','REJECTED') AND COALESCE(t.is_active,0)=0
                    """ + projectClause + " ORDER BY t.updated_at DESC,t.id DESC LIMIT 200", scopedArguments.toArray()));

            List<Object> financeArguments = new ArrayList<>();
            financeArguments.add(tenantId());
            if (!allScope) financeArguments.addAll(projectIds);
            financeArguments.add(tenantId());
            if (!allScope) financeArguments.addAll(projectIds);
            String entryScope = allScope ? "" : " AND e.project_id IN (" + placeholders(projectIds) + ")";
            String expenseScope = allScope ? "" : " AND x.project_id IN (" + placeholders(projectIds) + ")";
            result.put("financeSources", jdbc.queryForList("SELECT * FROM (" + """
                    SELECT 'ACCOUNTING_ENTRY_LINE' sourceType,l.id sourceId,e.project_id projectId,
                           CONCAT(e.entry_code,' / ',l.line_no) sourceCode,l.summary sourceName,
                           l.amount-COALESCE((SELECT SUM(b.source_amount) FROM finance_cost_allocation_batch b
                             WHERE b.tenant_id=l.tenant_id AND b.source_type='ACCOUNTING_ENTRY_LINE'
                               AND b.source_id=l.id),0) remainingAmount
                    FROM accounting_entry_line l
                    JOIN accounting_entry e ON e.tenant_id=l.tenant_id AND e.id=l.entry_id
                    JOIN cost_subject s ON s.tenant_id=l.tenant_id AND s.id=l.cost_subject_id
                    WHERE l.tenant_id=? AND e.deleted_flag=0 AND e.entry_status='POSTED' AND e.source_type='MANUAL'
                      AND l.direction='DEBIT' AND s.deleted_flag=0 AND s.status='ENABLE' AND s.account_category='COST'
                    """ + entryScope + " UNION ALL " + """
                    SELECT 'EXPENSE_APPLICATION' sourceType,x.id sourceId,x.project_id projectId,
                           x.expense_code sourceCode,x.description sourceName,
                           x.amount-COALESCE((SELECT SUM(b.source_amount) FROM finance_cost_allocation_batch b
                             WHERE b.tenant_id=x.tenant_id AND b.source_type='EXPENSE_APPLICATION'
                               AND b.source_id=x.id),0) remainingAmount
                    FROM expense_application x JOIN cost_subject s ON s.tenant_id=x.tenant_id AND s.id=x.cost_subject_id
                    WHERE x.tenant_id=? AND x.deleted_flag=0 AND x.approval_status='APPROVED'
                      AND s.deleted_flag=0 AND s.status='ENABLE' AND s.account_category='COST'
                    """ + expenseScope + ") finance_source WHERE remainingAmount>0 ORDER BY sourceCode LIMIT 300",
                    financeArguments.toArray()));
        }
        return result;
    }

    Map<String, Object> projectConfiguration(Long projectId) {
        requireProject(projectId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", one("""
                SELECT p.id,p.project_code projectCode,p.project_name projectName,p.status projectStatus,
                       c.id mainContractId,c.contract_code mainContractCode,c.contract_name mainContractName,
                       c.current_amount mainContractAmount,
                       t.id targetId,t.version_no targetVersionNo,t.version_name targetVersionName,
                       t.total_target_amount targetAmount,t.approval_status targetStatus
                FROM pm_project p
                LEFT JOIN ct_contract c ON c.tenant_id=p.tenant_id AND c.id=p.owner_contract_id
                  AND c.deleted_flag=0
                LEFT JOIN cost_target t ON t.tenant_id=p.tenant_id AND t.project_id=p.id
                  AND t.status='ACTIVE' AND t.deleted_flag=0
                WHERE p.tenant_id=? AND p.id=? AND p.deleted_flag=0
                ORDER BY t.id DESC LIMIT 1
                """, projectId));
        result.put("subjects", jdbc.queryForList("""
                SELECT s.id,s.subject_code subjectCode,s.subject_name subjectName,s.subject_type subjectType,s.status,
                       COALESCE(sc.enabled,1) enabled,sc.effective_from effectiveFrom,sc.effective_to effectiveTo,
                       sc.config_request_id configRequestId,sc.configuration_version configurationVersion,
                       CASE WHEN sc.enabled=0 THEN 'EXCLUDED' WHEN sc.id IS NOT NULL THEN 'EXCEPTION' ELSE 'INHERITED' END scopeState,
                       (SELECT COUNT(*) FROM cost_item ci WHERE ci.tenant_id=s.tenant_id AND ci.project_id=?
                         AND ci.cost_subject_id=s.id AND ci.deleted_flag=0) costFactCount,
                       (SELECT COUNT(*) FROM cost_target_item ti WHERE ti.tenant_id=s.tenant_id AND ti.project_id=?
                         AND ti.cost_subject_id=s.id AND ti.deleted_flag=0) targetItemCount,
                       (SELECT COUNT(*) FROM project_budget_line bl WHERE bl.tenant_id=s.tenant_id AND bl.project_id=?
                         AND bl.cost_subject_id=s.id AND bl.deleted_flag=0) budgetLineCount,
                       (SELECT COUNT(*) FROM cost_subject_assignment_rule ar WHERE ar.tenant_id=s.tenant_id
                         AND (ar.project_id=? OR ar.project_id IS NULL) AND ar.cost_subject_id=s.id) ruleCount
                FROM cost_subject s
                LEFT JOIN project_cost_subject_scope sc ON sc.tenant_id=s.tenant_id AND sc.project_id=?
                  AND sc.cost_subject_id=s.id
                WHERE s.tenant_id=? AND s.deleted_flag=0 AND s.account_category='COST'
                  AND NOT EXISTS (SELECT 1 FROM cost_subject c WHERE c.tenant_id=s.tenant_id
                                  AND c.parent_id=s.id AND c.deleted_flag=0)
                ORDER BY s.subject_code,s.id
                """, projectId, projectId, projectId, projectId, projectId, tenantId()));
        List<Map<String, Object>> requests = jdbc.queryForList("""
                SELECT id,request_code requestCode,project_status_snapshot projectStatusSnapshot,direct_apply directApply,
                       status,approval_instance_id approvalInstanceId,applied_at appliedAt,reason,created_at createdAt
                FROM cost_project_config_request WHERE tenant_id=? AND project_id=?
                ORDER BY created_at DESC LIMIT 50
                """, tenantId(), projectId);
        for (Map<String, Object> request : requests) {
            request.put("lines", projectConfigLines(longValue(request.get("id"))));
        }
        result.put("requests", requests);
        return result;
    }

    Map<String, Object> createProjectConfig(ProjectConfigCommand command) {
        requireText(command.reason(), "调整原因不能为空");
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new BusinessException("COST_PROJECT_CONFIG_LINES_REQUIRED", "至少选择一项排除或例外科目");
        }
        if (command.lines().size() > APPROVAL_DETAIL_ROW_LIMIT) {
            throw new BusinessException("COST_PROJECT_CONFIG_LINE_LIMIT_EXCEEDED", "单个项目成本配置申请最多维护1000条科目调整");
        }
        requireProject(command.projectId());
        Map<String, Object> project = one("""
                SELECT id,status projectStatus FROM pm_project
                WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE
                """, command.projectId());
        String projectStatus = String.valueOf(project.get("projectStatus"));
        if ("CLOSED".equals(projectStatus)) {
            throw new BusinessException("COST_PROJECT_CONFIG_CLOSED_READ_ONLY", "已关闭项目配置只读；请使用关闭后财务调整流程");
        }
        Long costFacts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_item WHERE tenant_id=? AND project_id=? AND deleted_flag=0
                """, Long.class, tenantId(), command.projectId());
        boolean direct = "PREPARING".equals(projectStatus) && (costFacts == null || costFacts == 0);
        int baseConfigurationVersion = currentConfigurationVersion(command.projectId());
        requireLeafSubjects(command.lines().stream().map(ProjectConfigLine::costSubjectId).toList());
        Long requestId = IdWorker.getId();
        String requestCode = "CPC-" + LocalDate.now().toString().replace("-", "") + "-" + tail(requestId);
        jdbc.update("""
                INSERT INTO cost_project_config_request
                (id,tenant_id,request_code,project_id,project_status_snapshot,base_configuration_version,direct_apply,status,
                 version,created_by,reason)
                VALUES (?,?,?,?,?,?,?, 'DRAFT',0,?,?)
                """, requestId, tenantId(), requestCode, command.projectId(), projectStatus,
                baseConfigurationVersion, direct ? 1 : 0, userId(), command.reason().trim());
        for (ProjectConfigLine line : command.lines()) {
            LocalDate from = line.effectiveFrom() == null ? LocalDate.now() : line.effectiveFrom();
            if (line.effectiveTo() != null && line.effectiveTo().isBefore(from)) {
                throw new BusinessException("COST_PROJECT_CONFIG_DATE_INVALID", "配置失效日不能早于生效日");
            }
            jdbc.update("""
                    INSERT INTO cost_project_config_request_line
                    (id,tenant_id,request_id,cost_subject_id,enabled,effective_from,effective_to,impact_snapshot)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, IdWorker.getId(), tenantId(), requestId, line.costSubjectId(),
                    Boolean.FALSE.equals(line.enabled()) ? 0 : 1, from, line.effectiveTo(),
                    json(impactSnapshot(command.projectId(), line.costSubjectId())));
        }
        if (direct) {
            applyProjectConfig(requestId, null);
        }
        return projectConfigRequest(requestId);
    }

    Map<String, Object> submitProjectConfig(Long id) {
        Map<String, Object> request = projectConfigRequestForUpdate(id);
        requireCurrentUserCreated(request.get("createdBy"), "项目成本配置申请");
        requireProjectOpenForNormalCostGovernance(longValue(request.get("projectId")));
        if (intValue(request.get("directApply")) == 1) {
            throw new BusinessException("COST_PROJECT_CONFIG_ALREADY_APPLIED", "筹备期配置已直接生效，无需审批");
        }
        String status = String.valueOf(request.get("status"));
        Long instanceId = longValue(request.get("approvalInstanceId"));
        if (!("DRAFT".equals(status) || "REJECTED".equals(status) || "WITHDRAWN".equals(status))) {
            throw new BusinessException("COST_PROJECT_CONFIG_NOT_SUBMITTABLE", "当前项目配置申请不能提交");
        }
        assertNoCompetingProjectConfig(longValue(request.get("projectId")), id);
        WorkflowEngine engine = workflowEngineProvider.getObject();
        if (instanceId == null) {
            engine.submitCostGovernance(userId(), UserContext.getCurrentUsername(), tenantId(),
                    WorkflowBusinessTypes.COST_PROJECT_CONFIG, id, "项目成本配置 " + request.get("requestCode"),
                    BigDecimal.ZERO, longValue(request.get("projectId")), null,
                    String.valueOf(request.get("reason")), null, null);
        } else {
            engine.resubmitCostGovernance(instanceId, userId(), UserContext.getCurrentUsername());
        }
        return projectConfigRequest(id);
    }

    Map<String, Object> cancelProjectConfig(Long id) {
        Map<String, Object> request = projectConfigRequestForUpdate(id);
        requireCurrentUserCreated(request.get("createdBy"), "项目成本配置申请");
        requireProject(longValue(request.get("projectId")));
        if (!"DRAFT".equals(request.get("status")) || request.get("approvalInstanceId") != null
                || intValue(request.get("directApply")) == 1) {
            throw new BusinessException("COST_PROJECT_CONFIG_NOT_CANCELLABLE", "仅未提交审批的非直配草稿可取消");
        }
        int updated = jdbc.update("""
                UPDATE cost_project_config_request SET status='CANCELLED',updated_by=?,
                    updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status='DRAFT' AND direct_apply=0
                  AND approval_instance_id IS NULL
                """, userId(), tenantId(), id);
        if (updated != 1) {
            throw new BusinessException("COST_PROJECT_CONFIG_STATE_INVALID", "项目配置申请状态已变化");
        }
        return projectConfigRequest(id);
    }

    private void assertNoCompetingProjectConfig(Long projectId, Long requestId) {
        one("SELECT id FROM pm_project WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE", projectId);
        Integer competing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_project_config_request
                WHERE tenant_id=? AND project_id=? AND id<>? AND status IN('DRAFT','SUBMITTED')
                """, Integer.class, tenantId(), projectId, requestId);
        if (competing != null && competing > 0) {
            throw new BusinessException("COST_PROJECT_CONFIG_ACTIVE_CONFLICT", "当前项目已有活动配置申请");
        }
    }

    void markProjectConfigSubmitted(Long id, Long instanceId) {
        int updated = jdbc.update("""
                UPDATE cost_project_config_request SET status='SUBMITTED',approval_instance_id=?,updated_by=?,
                 updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND direct_apply=0 AND status IN('DRAFT','REJECTED','WITHDRAWN')
                  AND (approval_instance_id IS NULL OR approval_instance_id=?)
                """, instanceId, userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("COST_PROJECT_CONFIG_STATE_INVALID", "项目配置申请状态已变化");
    }

    void applyProjectConfig(Long id, Long instanceId) {
        Map<String, Object> request = projectConfigRequestForUpdate(id);
        requireProject(longValue(request.get("projectId")));
        boolean direct = intValue(request.get("directApply")) == 1;
        if (!direct) {
            requireApprovedWorkflow(instanceId, WorkflowBusinessTypes.COST_PROJECT_CONFIG, id);
        }
        if (!(direct && "DRAFT".equals(request.get("status"))
                || !direct && "SUBMITTED".equals(request.get("status")))) {
            throw new BusinessException("COST_PROJECT_CONFIG_STATE_INVALID", "项目配置申请状态已变化");
        }
        Long projectId = longValue(request.get("projectId"));
        Map<String, Object> currentProject = one("""
                SELECT id,status projectStatus FROM pm_project
                WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE
                """, projectId);
        if ("CLOSED".equals(currentProject.get("projectStatus"))) {
            throw new BusinessException("COST_PROJECT_CONFIG_CLOSED_READ_ONLY", "审批期间项目已关闭，配置不得生效");
        }
        int currentVersion = currentConfigurationVersion(projectId);
        int baseVersion = intValue(request.get("baseConfigurationVersion"));
        if (currentVersion != baseVersion) {
            throw new BusinessException("COST_PROJECT_CONFIG_STALE", "项目成本配置已变化，请基于最新版本重新申请");
        }
        int configVersion = currentVersion + 1;
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT cost_subject_id,enabled,effective_from,effective_to
                FROM cost_project_config_request_line WHERE tenant_id=? AND request_id=? ORDER BY id
                """, tenantId(), id);
        List<Long> subjectIds = lines.stream()
                .map(line -> longValue(line.get("cost_subject_id")))
                .filter(Objects::nonNull)
                .distinct().sorted().toList();
        if (!subjectIds.isEmpty()) {
            List<Object> subjectLockArgs = new ArrayList<>();
            subjectLockArgs.add(tenantId());
            subjectLockArgs.addAll(subjectIds);
            jdbc.queryForList(("""
                    SELECT id FROM cost_subject
                    WHERE tenant_id=? AND id IN (%s) ORDER BY id FOR UPDATE
                    """).formatted(placeholders(subjectIds)), Long.class, subjectLockArgs.toArray());
        }
        for (Map<String, Object> line : lines) {
            requireSubject(longValue(line.get("cost_subject_id")), true);
            LocalDate effectiveFrom = localDate(line.get("effective_from"));
            LocalDate effectiveTo = localDate(line.get("effective_to"));
            if (effectiveFrom == null || effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
                throw new BusinessException("COST_PROJECT_CONFIG_DATE_INVALID", "审批时项目成本配置日期已失效");
            }
        }
        for (Map<String, Object> line : lines) {
            Long subjectId = longValue(line.get("cost_subject_id"));
            int updated = jdbc.update("""
                    UPDATE project_cost_subject_scope
                    SET enabled=?,effective_from=?,effective_to=?,config_request_id=?,configuration_version=?,
                        updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1
                    WHERE tenant_id=? AND project_id=? AND cost_subject_id=?
                    """, intValue(line.get("enabled")), line.get("effective_from"), line.get("effective_to"),
                    id, configVersion, userId(), tenantId(), projectId, subjectId);
            if (updated == 0) {
                jdbc.update("""
                        INSERT INTO project_cost_subject_scope
                        (id,tenant_id,project_id,config_request_id,configuration_version,cost_subject_id,enabled,
                         effective_from,effective_to,version,created_by,remark)
                        VALUES (?,?,?,?,?,?,?,?,?,0,?,?)
                        """, IdWorker.getId(), tenantId(), projectId, id, configVersion, subjectId,
                        intValue(line.get("enabled")), line.get("effective_from"), line.get("effective_to"),
                        userId(), "项目成本配置申请 " + request.get("requestCode"));
            }
            jdbc.update("""
                    INSERT INTO project_cost_subject_scope_history
                    (id,tenant_id,project_id,config_request_id,configuration_version,cost_subject_id,enabled,
                     effective_from,effective_to,recorded_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, IdWorker.getId(), tenantId(), projectId, id, configVersion, subjectId,
                    intValue(line.get("enabled")), line.get("effective_from"), line.get("effective_to"), userId(),
                    "项目成本配置申请 " + request.get("requestCode"));
        }
        jdbc.update("""
                UPDATE cost_project_config_request SET status='APPLIED',applied_at=CURRENT_TIMESTAMP,
                 updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE tenant_id=? AND id=?
                """, userId(), tenantId(), id);
    }

    void rejectProjectConfig(Long id, Long instanceId, String status) {
        String target = "WITHDRAWN".equals(status) ? "WITHDRAWN" : "REJECTED";
        int updated = jdbc.update("""
                UPDATE cost_project_config_request SET status=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status='SUBMITTED' AND approval_instance_id=?
                """, target, userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("COST_PROJECT_CONFIG_STATE_INVALID", "项目配置申请状态已变化");
    }

    List<Map<String, Object>> reversalRequests() {
        boolean allScope = projectAccessChecker.hasAllScope();
        List<Long> projectIds = projectAccessChecker.accessibleProjectIds();
        if (projectIds.isEmpty() && !allScope) return List.of();
        List<Object> args = new ArrayList<>();
        args.add(tenantId());
        String accessClause = "";
        if (!allScope) {
            String allowed = placeholders(projectIds);
            accessClause = """
                     AND (
                       (r.target_type='FINANCE_ALLOCATION' AND NOT EXISTS (
                         SELECT 1 FROM finance_cost_allocation_line fl
                         WHERE fl.tenant_id=r.tenant_id AND fl.batch_id=r.target_id
                           AND fl.project_id NOT IN (%s)))
                       OR (r.target_type='RECALCULATION' AND NOT EXISTS (
                         SELECT 1 FROM cost_recalculation_line rl
                         JOIN cost_item rf ON rf.tenant_id=rl.tenant_id AND rf.id=rl.original_cost_item_id
                         WHERE rl.tenant_id=r.tenant_id AND rl.batch_id=r.target_id
                           AND rf.project_id NOT IN (%s)))
                       OR (r.target_type='BID_TRANSFER' AND r.project_id IN (%s))
                     )
                    """.formatted(allowed, allowed, allowed);
            args.addAll(projectIds);
            args.addAll(projectIds);
            args.addAll(projectIds);
        }
        return jdbc.queryForList("""
                SELECT r.id,r.request_code requestCode,r.target_type targetType,r.target_id targetId,
                       r.project_id projectId,p.project_name projectName,r.status,
                       r.approval_instance_id approvalInstanceId,r.final_record_id finalRecordId,
                       r.created_by createdBy,r.created_at createdAt,r.reason
                FROM cost_reversal_request r
                LEFT JOIN pm_project p ON p.tenant_id=r.tenant_id AND p.id=r.project_id
                WHERE r.tenant_id=?
                """ + accessClause + " ORDER BY r.created_at DESC,r.id DESC LIMIT 100", args.toArray());
    }

    Map<String, Object> createReversal(ReversalCommand command) {
        requireText(command.targetType(), "冲销对象类型不能为空");
        requireText(command.reason(), "冲销原因不能为空");
        if (command.targetId() == null) {
            throw new BusinessException("COST_REVERSAL_TARGET_REQUIRED", "冲销对象不能为空");
        }
        String targetType = command.targetType().trim().toUpperCase(java.util.Locale.ROOT);
        Map<String, Object> target = reversibleTarget(targetType, command.targetId());
        Long projectId = longValue(target.get("projectId"));
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO cost_reversal_request
                    (id,tenant_id,request_code,target_type,target_id,project_id,status,version,created_by,reason)
                    VALUES (?,?,?,?,?,?,'DRAFT',0,?,?)
                    """, id, tenantId(), "CRR-" + LocalDate.now().toString().replace("-", "") + "-" + tail(id),
                    targetType, command.targetId(), projectId, userId(), command.reason().trim());
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("COST_REVERSAL_REQUEST_DUPLICATE", "该成本事实已有冲销申请", ex);
        }
        return reversalRequest(id);
    }

    Map<String, Object> submitReversal(Long id) {
        Map<String, Object> request = reversalRequestForUpdate(id);
        requireCurrentUserCreated(request.get("createdBy"), "成本冲销申请");
        String status = String.valueOf(request.get("status"));
        if (!List.of("DRAFT", "REJECTED", "WITHDRAWN").contains(status)) {
            throw new BusinessException("COST_REVERSAL_NOT_SUBMITTABLE", "当前冲销申请不能提交");
        }
        reversibleTarget(String.valueOf(request.get("targetType")), longValue(request.get("targetId")));
        Long instanceId = longValue(request.get("approvalInstanceId"));
        WorkflowEngine engine = workflowEngineProvider.getObject();
        if (instanceId == null) {
            engine.submitCostGovernance(userId(), UserContext.getCurrentUsername(), tenantId(),
                    WorkflowBusinessTypes.COST_REVERSAL, id,
                    "成本冲销 " + request.get("requestCode"), BigDecimal.ZERO,
                    longValue(request.get("projectId")), null, String.valueOf(request.get("reason")), null, null);
        } else {
            engine.resubmitCostGovernance(instanceId, userId(), UserContext.getCurrentUsername());
        }
        return reversalRequest(id);
    }

    Map<String, Object> cancelReversal(Long id) {
        Map<String, Object> request = reversalRequestForUpdate(id);
        requireCurrentUserCreated(request.get("createdBy"), "成本冲销申请");
        String status = String.valueOf(request.get("status"));
        if (!List.of("DRAFT", "REJECTED", "WITHDRAWN").contains(status)) {
            throw new BusinessException("COST_REVERSAL_NOT_CANCELLABLE", "仅草稿、已驳回或已撤回的冲销申请可取消");
        }
        int updated = jdbc.update("""
                UPDATE cost_reversal_request
                SET status='CANCELLED',updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status IN ('DRAFT','REJECTED','WITHDRAWN')
                """, userId(), tenantId(), id);
        if (updated != 1) {
            throw new BusinessException("COST_REVERSAL_NOT_CANCELLABLE", "冲销申请状态已变化");
        }
        return reversalRequest(id);
    }

    void markReversalSubmitted(Long id, Long instanceId) {
        int updated = jdbc.update("""
                UPDATE cost_reversal_request SET status='SUBMITTED',approval_instance_id=?,updated_by=?,
                 updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status IN ('DRAFT','REJECTED','WITHDRAWN')
                  AND (approval_instance_id IS NULL OR approval_instance_id=?)
                """, instanceId, userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("COST_REVERSAL_STATE_INVALID", "成本冲销申请状态已变化");
    }

    Map<String, Object> reversalRequestForPost(Long id, Long instanceId) {
        Map<String, Object> request = reversalRequestForUpdate(id);
        requireApprovedWorkflow(instanceId, WorkflowBusinessTypes.COST_REVERSAL, id);
        if (!"SUBMITTED".equals(request.get("status"))
                || !Objects.equals(longValue(request.get("approvalInstanceId")), instanceId)) {
            throw new BusinessException("COST_REVERSAL_STATE_INVALID", "成本冲销申请未处于当前审批中");
        }
        String targetType = String.valueOf(request.get("targetType"));
        if ("FINANCE_ALLOCATION".equals(targetType) || "RECALCULATION".equals(targetType)) {
            accountingPeriodGuard.assertWritable(LocalDate.now());
        }
        reversibleTarget(targetType, longValue(request.get("targetId")));
        return request;
    }

    void completeReversal(Long id, Long instanceId, Long finalId) {
        int updated = jdbc.update("""
                UPDATE cost_reversal_request SET status='POSTED',final_record_id=?,updated_by=?,
                 updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status='SUBMITTED' AND approval_instance_id=?
                """, finalId, userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("COST_REVERSAL_STATE_INVALID", "成本冲销申请终态写入失败");
    }

    void rejectReversal(Long id, Long instanceId, String status) {
        String target = "WITHDRAWN".equals(status) ? "WITHDRAWN" : "REJECTED";
        int updated = jdbc.update("""
                UPDATE cost_reversal_request SET status=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status='SUBMITTED' AND approval_instance_id=?
                """, target, userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("COST_REVERSAL_STATE_INVALID", "成本冲销申请状态已变化");
    }

    Map<String, Object> reversalRequest(Long id) {
        Map<String, Object> request = one("""
                SELECT id,request_code requestCode,target_type targetType,target_id targetId,project_id projectId,
                       status,approval_instance_id approvalInstanceId,final_record_id finalRecordId,
                       created_by createdBy,created_at createdAt,reason
                FROM cost_reversal_request WHERE tenant_id=? AND id=?
                """, id);
        String targetType = String.valueOf(request.get("targetType"));
        List<Long> projectIds = reversalReadProjectIds(targetType, longValue(request.get("targetId")),
                longValue(request.get("projectId")));
        if (projectIds.isEmpty()) projectAccessChecker.requireAllScope("查看");
        else projectIds.forEach(projectId -> projectAccessChecker.checkAccess(projectId, "查看成本冲销"));
        return request;
    }

    private List<Long> reversalReadProjectIds(String targetType, Long targetId, Long requestProjectId) {
        return switch (targetType) {
            case "FINANCE_ALLOCATION" -> jdbc.queryForList("""
                    SELECT DISTINCT project_id FROM finance_cost_allocation_line
                    WHERE tenant_id=? AND batch_id=? ORDER BY project_id
                    """, Long.class, tenantId(), targetId);
            case "RECALCULATION" -> recalculationProjectIds(targetId);
            case "BID_TRANSFER" -> requestProjectId == null ? List.of() : List.of(requestProjectId);
            default -> List.of();
        };
    }

    private Map<String, Object> reversalRequestForUpdate(Long id) {
        return one("""
                SELECT id,request_code requestCode,target_type targetType,target_id targetId,project_id projectId,
                       status,approval_instance_id approvalInstanceId,final_record_id finalRecordId,
                       created_by createdBy,reason
                FROM cost_reversal_request WHERE tenant_id=? AND id=? FOR UPDATE
                """, id);
    }

    private Map<String, Object> reversibleTarget(String targetType, Long targetId) {
        Map<String, Object> result = switch (targetType) {
            case "BID_TRANSFER" -> one("""
                    SELECT h.id,h.project_id projectId,h.status,
                           t.approval_status targetApprovalStatus,t.is_active targetActive
                    FROM bid_cost_target_transfer h
                    JOIN cost_target t ON t.tenant_id=h.tenant_id AND t.id=h.target_id AND t.deleted_flag=0
                    WHERE h.tenant_id=? AND h.id=? AND h.reversal_of_id IS NULL FOR UPDATE
                    """, targetId);
            case "FINANCE_ALLOCATION" -> {
                Map<String, Object> row = one("""
                        SELECT b.id,b.status FROM finance_cost_allocation_batch b
                        WHERE b.tenant_id=? AND b.id=? AND b.reversal_of_id IS NULL FOR UPDATE
                        """, targetId);
                List<Long> projects = jdbc.queryForList("""
                        SELECT DISTINCT project_id FROM finance_cost_allocation_line
                        WHERE tenant_id=? AND batch_id=?
                        ORDER BY project_id
                        """, Long.class, tenantId(), targetId);
                if (projects.isEmpty()) throw new BusinessException("COST_REVERSAL_TARGET_INVALID", "财务分摊缺少项目明细");
                row.put("projectId", projects.getFirst());
                row.put("projectIds", projects);
                yield row;
            }
            case "RECALCULATION" -> one("""
                    SELECT id,project_id projectId,status FROM cost_recalculation_batch
                    WHERE tenant_id=? AND id=? AND batch_type<>'REVERSAL' FOR UPDATE
                    """, targetId);
            default -> throw new BusinessException("COST_REVERSAL_TARGET_INVALID", "不支持的成本冲销对象");
        };
        if (!"POSTED".equals(result.get("status"))) {
            throw new BusinessException("COST_REVERSAL_TARGET_NOT_POSTED", "仅已过账且未冲销的成本事实可申请冲销");
        }
        if ("BID_TRANSFER".equals(targetType)
                && (!List.of("DRAFT", "REJECTED").contains(String.valueOf(result.get("targetApprovalStatus")))
                || intValue(result.get("targetActive")) == 1)) {
            throw new BusinessException("COST_TARGET_NOT_EDITABLE", "目标成本已审批或生效，须通过新目标版本调整，不能直接冲销转入");
        }
        List<Long> projectIds = switch (targetType) {
            case "BID_TRANSFER" -> List.of(longValue(result.get("projectId")));
            case "FINANCE_ALLOCATION" -> longList(result.get("projectIds"));
            case "RECALCULATION" -> recalculationProjectIds(targetId);
            default -> List.of();
        };
        lockReversalProjects(projectIds);
        Long projectId = longValue(result.get("projectId"));
        if (projectId == null) projectAccessChecker.requireAllScope("冲销");
        else projectIds.forEach(id -> projectAccessChecker.checkAccess(id, "冲销成本事实"));
        return result;
    }

    private void lockReversalProjects(List<Long> projectIds) {
        List<Long> ids = projectIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) {
            throw new BusinessException("COST_REVERSAL_TARGET_INVALID", "成本冲销对象缺少项目事实");
        }
        List<Object> args = new ArrayList<>();
        args.add(tenantId());
        args.addAll(ids);
        List<Long> locked = jdbc.queryForList("""
                SELECT id FROM pm_project
                WHERE tenant_id=? AND id IN (%s) AND deleted_flag=0 ORDER BY id FOR UPDATE
                """.formatted(placeholders(ids)), Long.class, args.toArray());
        if (!locked.equals(ids)) {
            throw new BusinessException("COST_REVERSAL_PROJECT_INVALID", "成本冲销包含不存在或已失效项目");
        }
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(item -> longValue(item)).filter(Objects::nonNull).toList();
    }

    List<Map<String, Object>> recalculationBatches() {
        boolean allScope = projectAccessChecker.hasAllScope();
        List<Long> projectIds = projectAccessChecker.accessibleProjectIds();
        if (projectIds.isEmpty() && !allScope) return List.of();
        List<Object> args = new ArrayList<>();
        args.add(tenantId());
        if (!allScope) args.addAll(projectIds);
        String accessClause = allScope ? "" : " AND b.project_id IN (" + placeholders(projectIds) + ")";
        return jdbc.queryForList("""
                SELECT b.id,b.batch_code batchCode,b.batch_type batchType,b.project_id projectId,p.project_name projectName,
                       b.cutoff_at cutoffAt,b.rule_version_id ruleVersionId,v.version_code ruleVersionCode,b.status,
                       b.original_fact_count originalFactCount,b.changed_fact_count changedFactCount,
                       b.unclassified_count unclassifiedCount,b.original_total originalTotal,
                       b.adjustment_total adjustmentTotal,b.approval_instance_id approvalInstanceId,b.created_at createdAt,b.reason
                FROM cost_recalculation_batch b
                LEFT JOIN pm_project p ON p.tenant_id=b.tenant_id AND p.id=b.project_id
                JOIN cost_subject_mapping_version v ON v.tenant_id=b.tenant_id AND v.id=b.rule_version_id
                WHERE b.tenant_id=?
                """ + accessClause + " ORDER BY b.created_at DESC LIMIT 100", args.toArray());
    }

    Map<String, Object> createRecalculation(RecalculationCommand command) {
        requireText(command.reason(), "重算原因不能为空");
        String batchType = textOrDefault(command.batchType(), "HISTORY_RECALCULATION");
        if (!("HISTORY_RECALCULATION".equals(batchType) || "POST_CLOSE_ADJUSTMENT".equals(batchType))) {
            throw new BusinessException("COST_RECALCULATION_TYPE_INVALID", "不支持的重算类型");
        }
        if (command.projectId() != null) requireProject(command.projectId());
        else projectAccessChecker.requireAllScope("发起");
        requireRecalculationProjectState(batchType, command.projectId());
        requireMappingVersion(command.ruleVersionId(), "ACTIVE");
        LocalDateTime cutoff = command.cutoffAt() == null ? LocalDateTime.now() : command.cutoffAt();
        Long batchId = IdWorker.getId();
        List<Map<String, Object>> facts = loadRecalculationFacts(command.projectId(), cutoff, batchType);
        lockAndValidateRecalculationProjects(batchType, facts.stream()
                .map(fact -> longValue(fact.get("project_id"))).filter(Objects::nonNull).toList());
        List<SnapshotEntry> snapshots = new ArrayList<>();
        List<TrialLine> trials = new ArrayList<>();
        int unclassified = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> fact : facts) {
            String factHash = factHash(fact);
            snapshots.add(new SnapshotEntry(longValue(fact.get("id")), factHash,
                    Objects.toString(fact.get("cost_subject_id"), "")));
            total = total.add(money(fact.get("amount")));
            CostSubjectResolver.Decision decision = null;
            try {
                decision = resolver.resolveForVersion(tenantId(), longValue(fact.get("project_id")),
                        String.valueOf(fact.get("root_source_type")),
                        String.valueOf(fact.get("classification_business_category")),
                        longValue(fact.get("cost_subject_id")), command.ruleVersionId(),
                        dateValue(fact.get("cost_date"), fact.get("created_at")));
            } catch (BusinessException ex) {
                if (!List.of("COST_SUBJECT_UNCLASSIFIED", "COST_SUBJECT_RULE_AMBIGUOUS",
                        "COST_SUBJECT_NOT_IN_PROJECT_SCOPE").contains(ex.getCode())) throw ex;
                unclassified++;
            }
            String difference = decision == null ? "UNCLASSIFIED"
                    : Objects.equals(longValue(fact.get("cost_subject_id")), decision.costSubjectId())
                    ? "UNCHANGED" : "RECLASSIFY";
            trials.add(new TrialLine(fact, decision, difference, factHash));
        }
        String sourceHash = sha256(json(snapshots));
        int changed = (int) trials.stream().filter(line -> "RECLASSIFY".equals(line.difference())).count();
        try {
            jdbc.update("""
                    INSERT INTO cost_recalculation_batch
                    (id,tenant_id,batch_code,batch_type,project_id,scope_key,cutoff_at,source_snapshot_hash,
                     idempotency_key,rule_version_id,status,original_fact_count,changed_fact_count,unclassified_count,
                     original_total,adjustment_total,old_snapshot,difference_report,version,created_by,reason)
                    VALUES (?,?,?,?,?,?,?,?,?,?, 'DRAFT',?,?,?,?,0,?,?,0,?,?)
                    """, batchId, tenantId(), "CRB-" + LocalDate.now().toString().replace("-", "") + "-" + tail(batchId),
                    batchType, command.projectId(), command.projectId() == null ? "ALL" : String.valueOf(command.projectId()),
                    Timestamp.valueOf(cutoff), sourceHash, "CRB-IDEMP-" + batchId, command.ruleVersionId(),
                    facts.size(), changed, unclassified, total, json(snapshots),
                    json(Map.of("originalFactCount", facts.size(), "changedFactCount", changed,
                            "unclassifiedCount", unclassified, "grossTotal", total)),
                    userId(), command.reason().trim());
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("COST_RECALCULATION_ACTIVE_CONFLICT", "同一范围已有活动重算批次或幂等键已使用", ex);
        }
        for (TrialLine line : trials) insertRecalculationLine(batchId, line);
        requireNoPendingOverheadFacts(batchId);
        return recalculationBatch(batchId);
    }

    Map<String, Object> submitRecalculation(Long id) {
        Map<String, Object> batch = recalculationBatchForUpdate(id);
        requireCurrentUserCreated(batch.get("createdBy"), "成本历史重算批次");
        authorizeRecalculationBatch(batch, "提交");
        requireRecalculationProjectState(String.valueOf(batch.get("batchType")), longValue(batch.get("projectId")));
        if (intValue(batch.get("unclassifiedCount")) > 0) {
            throw new BusinessException("COST_RECALCULATION_UNCLASSIFIED", "历史试算仍有待归类事实，禁止提交");
        }
        String status = String.valueOf(batch.get("status"));
        Long instanceId = longValue(batch.get("approvalInstanceId"));
        if (!("DRAFT".equals(status) || "REJECTED".equals(status) || "WITHDRAWN".equals(status))) {
            throw new BusinessException("COST_RECALCULATION_NOT_SUBMITTABLE", "当前重算批次不能提交");
        }
        assertNoCompetingRecalculation(batch, id);
        assertRecalculationPeriodsWritable(id);
        lockAndValidateRecalculationProjects(String.valueOf(batch.get("batchType")), recalculationProjectIds(id));
        lockRecalculationFacts(id);
        verifyRecalculationSnapshot(batch);
        requireNoPendingOverheadFacts(id);
        reserveRecalculationFacts(id);
        String businessType = "POST_CLOSE_ADJUSTMENT".equals(batch.get("batchType"))
                ? WorkflowBusinessTypes.COST_POST_CLOSE_ADJUSTMENT : WorkflowBusinessTypes.COST_RECALCULATION;
        WorkflowEngine engine = workflowEngineProvider.getObject();
        if (instanceId == null) {
            engine.submitCostGovernance(userId(), UserContext.getCurrentUsername(), tenantId(), businessType, id,
                    "成本历史重算 " + batch.get("batchCode"), money(batch.get("originalTotal")),
                    longValue(batch.get("projectId")), null, String.valueOf(batch.get("reason")), null, null);
        } else {
            engine.resubmitCostGovernance(instanceId, userId(), UserContext.getCurrentUsername());
        }
        return recalculationBatch(id);
    }

    void markRecalculationSubmitted(Long id, Long instanceId) {
        int updated = jdbc.update("""
                UPDATE cost_recalculation_batch SET status='SUBMITTED',approval_instance_id=?,updated_by=?,
                 updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status IN('DRAFT','REJECTED','WITHDRAWN')
                  AND (approval_instance_id IS NULL OR approval_instance_id=?)
                """, instanceId, userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("COST_RECALCULATION_STATE_INVALID", "重算批次状态已变化");
    }

    void postRecalculation(Long id, Long instanceId) {
        Map<String, Object> batch = recalculationBatchForUpdate(id);
        authorizeRecalculationBatch(batch, "过账");
        requireRecalculationProjectState(String.valueOf(batch.get("batchType")), longValue(batch.get("projectId")));
        String businessType = "POST_CLOSE_ADJUSTMENT".equals(batch.get("batchType"))
                ? WorkflowBusinessTypes.COST_POST_CLOSE_ADJUSTMENT : WorkflowBusinessTypes.COST_RECALCULATION;
        requireApprovedWorkflow(instanceId, businessType, id);
        if (!"SUBMITTED".equals(batch.get("status"))) {
            throw new BusinessException("COST_RECALCULATION_STATE_INVALID", "仅审批中的重算批次可过账");
        }
        assertRecalculationPeriodsWritable(id);
        lockAndValidateRecalculationProjects(String.valueOf(batch.get("batchType")), recalculationProjectIds(id));
        lockRecalculationFacts(id);
        verifyRecalculationSnapshot(batch);
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT line.*,original.project_id original_project_id
                FROM cost_recalculation_line line
                JOIN cost_item original ON original.tenant_id=line.tenant_id
                  AND original.id=line.original_cost_item_id AND original.deleted_flag=0
                WHERE line.tenant_id=? AND line.batch_id=? AND line.difference_type='RECLASSIFY'
                ORDER BY line.id FOR UPDATE
                """, tenantId(), id);
        lockRecalculationPostingSubjects(lines);
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            if (line.get("negative_cost_item_id") != null || line.get("positive_cost_item_id") != null) {
                throw new BusinessException("COST_RECALCULATION_ALREADY_POSTED", "重算调整事实已存在");
            }
            Map<String, Object> original = costFactForUpdate(longValue(line.get("original_cost_item_id")));
            Long negativeId = insertAdjustmentFact(batch, line, original, true);
            Long positiveId = insertAdjustmentFact(batch, line, original, false);
            int lineUpdated = jdbc.update("""
                    UPDATE cost_recalculation_line SET negative_cost_item_id=?,positive_cost_item_id=?
                    WHERE tenant_id=? AND id=? AND negative_cost_item_id IS NULL AND positive_cost_item_id IS NULL
                    """, negativeId, positiveId, tenantId(), longValue(line.get("id")));
            if (lineUpdated != 1) {
                throw new BusinessException("COST_RECALCULATION_LINE_CONFLICT", "重算明细并发状态已变化");
            }
            gross = gross.add(money(line.get("amount")).negate()).add(money(line.get("amount")));
            tax = tax.add(money(line.get("tax_amount")).negate()).add(money(line.get("tax_amount")));
            net = net.add(money(line.get("amount_without_tax")).negate()).add(money(line.get("amount_without_tax")));
        }
        if (gross.signum() != 0 || tax.signum() != 0 || net.signum() != 0) {
            throw new BusinessException("COST_RECALCULATION_NOT_CONSERVED", "重算调整金额、税额或不含税金额不守恒");
        }
        Map<String, Object> persisted = jdbc.queryForMap("""
                SELECT COUNT(*) fact_count,COALESCE(SUM(amount),0) gross_total,
                       COALESCE(SUM(tax_amount),0) tax_total,COALESCE(SUM(amount_without_tax),0) net_total
                FROM cost_item WHERE tenant_id=? AND adjustment_batch_id=? AND deleted_flag=0
                """, tenantId(), id);
        Integer brokenPairs = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT original_cost_item_id
                  FROM cost_item WHERE tenant_id=? AND adjustment_batch_id=? AND deleted_flag=0
                  GROUP BY original_cost_item_id
                  HAVING COUNT(*)<>2 OR COALESCE(SUM(amount),0)<>0 OR COALESCE(SUM(tax_amount),0)<>0
                     OR COALESCE(SUM(amount_without_tax),0)<>0
                ) conserved_pairs
                """, Integer.class, tenantId(), id);
        if (intValue(persisted.get("fact_count")) != lines.size() * 2
                || money(persisted.get("gross_total")).signum() != 0
                || money(persisted.get("tax_total")).signum() != 0
                || money(persisted.get("net_total")).signum() != 0
                || brokenPairs == null || brokenPairs != 0) {
            throw new BusinessException("COST_RECALCULATION_NOT_CONSERVED", "重算持久化事实未逐来源守恒");
        }
        int updated = jdbc.update("""
                UPDATE cost_recalculation_batch SET status='POSTED',adjustment_total=0,posted_at=CURRENT_TIMESTAMP,
                 updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status='SUBMITTED'
                """, userId(), tenantId(), id);
        if (updated != 1) throw new BusinessException("COST_RECALCULATION_STATE_INVALID", "重算批次状态已变化");
        jdbc.update("DELETE FROM cost_recalculation_fact_reservation WHERE tenant_id=? AND batch_id=?", tenantId(), id);
    }

    void cancelRecalculation(Long id) {
        Map<String, Object> batch = recalculationBatchForUpdate(id);
        authorizeRecalculationBatch(batch, "取消");
        if (!Objects.equals(longValue(batch.get("createdBy")), userId())) {
            throw new BusinessException("COST_RECALCULATION_CANCEL_DENIED", "仅发起人可取消重算草稿");
        }
        int updated = jdbc.update("""
                UPDATE cost_recalculation_batch SET status='CANCELLED',updated_by=?,updated_at=CURRENT_TIMESTAMP,
                 version=version+1 WHERE tenant_id=? AND id=? AND status='DRAFT'
                """, userId(), tenantId(), id);
        if (updated != 1) throw new BusinessException("COST_RECALCULATION_NOT_CANCELLABLE", "仅草稿重算批次可取消");
        jdbc.update("DELETE FROM cost_recalculation_fact_reservation WHERE tenant_id=? AND batch_id=?", tenantId(), id);
    }

    void rejectRecalculation(Long id, Long instanceId, String status) {
        Map<String, Object> batch = recalculationBatchForUpdate(id);
        authorizeRecalculationBatch(batch, "驳回或撤回");
        String target = "WITHDRAWN".equals(status) ? "WITHDRAWN" : "REJECTED";
        int updated = jdbc.update("""
                UPDATE cost_recalculation_batch SET status=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status='SUBMITTED' AND approval_instance_id=?
                """, target, userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("COST_RECALCULATION_STATE_INVALID", "重算批次状态已变化");
        jdbc.update("DELETE FROM cost_recalculation_fact_reservation WHERE tenant_id=? AND batch_id=?", tenantId(), id);
    }

    Long reverseRecalculationApproved(Long originalId, Long reversalRequestId,
                                      Long approvalInstanceId, String reason) {
        requireApprovedWorkflow(approvalInstanceId, WorkflowBusinessTypes.COST_REVERSAL, reversalRequestId);
        Map<String, Object> original = recalculationBatchForUpdate(originalId);
        authorizeRecalculationBatch(original, "冲销");
        if (!"POSTED".equals(original.get("status")) || "REVERSAL".equals(original.get("batchType"))) {
            throw new BusinessException("COST_RECALCULATION_NOT_REVERSIBLE", "仅已过账的原始重算批次可冲销");
        }
        List<Map<String, Object>> facts = jdbc.queryForList("""
                SELECT id,project_id,wbs_task_id,contract_id,partner_id,cost_subject_id,classification_status,
                       classification_business_category,recognition_role,
                       COALESCE(root_source_type,source_type) root_source_type,
                       mapping_version_id,assignment_rule_id,original_cost_subject_id,classification_override_id,
                       classification_snapshot_id,cost_type,amount,tax_amount,amount_without_tax,cost_date
                FROM cost_item WHERE tenant_id=? AND adjustment_batch_id=? AND deleted_flag=0
                ORDER BY id FOR UPDATE
                """, tenantId(), originalId);
        if (facts.isEmpty()) {
            throw new BusinessException("COST_RECALCULATION_NOT_REVERSIBLE", "该重算批次没有可冲销的调整事实");
        }
        lockHistoricalCostSubjects(facts.stream()
                .map(fact -> longValue(fact.get("cost_subject_id"))).filter(Objects::nonNull).toList());
        List<Long> postedDescendants = jdbc.queryForList("""
                SELECT successor.id
                FROM cost_item root
                JOIN cost_item successor
                  ON successor.tenant_id=root.tenant_id
                 AND successor.original_cost_item_id=root.id
                 AND successor.deleted_flag=0
                JOIN cost_recalculation_batch child_batch
                  ON child_batch.tenant_id=successor.tenant_id
                 AND child_batch.id=successor.adjustment_batch_id
                 AND child_batch.status='POSTED'
                WHERE root.tenant_id=? AND root.adjustment_batch_id=? AND root.deleted_flag=0
                ORDER BY successor.id FOR UPDATE
                """, Long.class, tenantId(), originalId);
        if (!postedDescendants.isEmpty()) {
            throw new BusinessException("COST_RECALCULATION_DESCENDANT_ACTIVE", "该批次已被后续重算承接，请先按叶子到祖先顺序冲销");
        }
        List<Long> reservedDescendants = jdbc.queryForList("""
                SELECT reservation.batch_id
                FROM cost_item root
                JOIN cost_recalculation_fact_reservation reservation
                  ON reservation.tenant_id=root.tenant_id
                 AND reservation.original_cost_item_id=root.id
                JOIN cost_recalculation_batch child_batch
                  ON child_batch.tenant_id=reservation.tenant_id
                 AND child_batch.id=reservation.batch_id
                 AND child_batch.status='SUBMITTED'
                WHERE root.tenant_id=? AND root.adjustment_batch_id=? AND root.deleted_flag=0
                ORDER BY reservation.batch_id FOR UPDATE
                """, Long.class, tenantId(), originalId);
        if (!reservedDescendants.isEmpty()) {
            throw new BusinessException("COST_RECALCULATION_DESCENDANT_RESERVED", "该批次正被后续重算占用，请先处理在途批次");
        }
        assertNoActiveOperationalDescendants(originalId);
        facts.stream().map(fact -> longValue(fact.get("project_id"))).distinct().forEach(this::requireProject);
        Long reversalId = IdWorker.getId();
        String snapshotHash = sha256("REVERSAL|" + originalId + "|" + facts.stream()
                .map(fact -> String.valueOf(fact.get("id"))).sorted().toList());
        try {
            jdbc.update("""
                    INSERT INTO cost_recalculation_batch
                    (id,tenant_id,batch_code,batch_type,project_id,scope_key,cutoff_at,source_snapshot_hash,
                     idempotency_key,rule_version_id,reversal_of_id,status,original_fact_count,changed_fact_count,
                     unclassified_count,original_total,adjustment_total,old_snapshot,difference_report,
                     approval_instance_id,posted_at,version,created_by,reason)
                    VALUES (?,?,?,'REVERSAL',?,?,CURRENT_TIMESTAMP,?,?,?,?, 'POSTED',?,?,0,0,0,?,?,?,CURRENT_TIMESTAMP,0,?,?)
                    """, reversalId, tenantId(), "CRBR-" + tail(reversalId), original.get("projectId"),
                    "REVERSAL:" + originalId, snapshotHash, "CRBR-IDEMP-" + reversalRequestId,
                    original.get("ruleVersionId"), originalId, facts.size(), facts.size(),
                    json(new SnapshotEntry(originalId, String.valueOf(original.get("sourceSnapshotHash")), "")),
                    json(Map.of("reversalOf", originalId, "factCount", facts.size())), approvalInstanceId,
                    userId(), reason);
            for (Map<String, Object> fact : facts) {
                jdbc.update("""
                        INSERT INTO cost_item
                        (id,tenant_id,project_id,wbs_task_id,contract_id,partner_id,cost_subject_id,
                         classification_status,classification_business_category,recognition_role,root_source_type,
                         mapping_version_id,assignment_rule_id,original_cost_subject_id,
                         classification_override_id,classification_snapshot_id,adjustment_batch_id,original_cost_item_id,
                         cost_type,amount,tax_amount,amount_without_tax,source_type,source_id,source_item_id,cost_date,
                         cost_status,generated_flag,created_by,remark)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_DATE,'CONFIRMED',1,?,?)
                        """, IdWorker.getId(), tenantId(), fact.get("project_id"), fact.get("wbs_task_id"),
                        fact.get("contract_id"), fact.get("partner_id"), fact.get("cost_subject_id"), "REVERSAL",
                        fact.get("classification_business_category"), fact.get("recognition_role"), fact.get("root_source_type"),
                        fact.get("mapping_version_id"), fact.get("assignment_rule_id"), fact.get("original_cost_subject_id"),
                        fact.get("classification_override_id"), fact.get("classification_snapshot_id"), reversalId,
                        fact.get("id"), fact.get("cost_type"), money(fact.get("amount")).negate(),
                        money(fact.get("tax_amount")).negate(), money(fact.get("amount_without_tax")).negate(),
                        "COST_RECALCULATION_REVERSAL", reversalId, fact.get("id"), userId(), reason);
            }
            Map<String, Object> conserved = jdbc.queryForMap("""
                    SELECT COUNT(*) fact_count,COALESCE(SUM(amount),0) gross_total,
                           COALESCE(SUM(tax_amount),0) tax_total,COALESCE(SUM(amount_without_tax),0) net_total
                    FROM cost_item WHERE tenant_id=? AND adjustment_batch_id=? AND deleted_flag=0
                    """, tenantId(), reversalId);
            if (intValue(conserved.get("fact_count")) != facts.size()
                    || money(conserved.get("gross_total")).signum() != 0
                    || money(conserved.get("tax_total")).signum() != 0
                    || money(conserved.get("net_total")).signum() != 0) {
                throw new BusinessException("COST_REVERSAL_NOT_CONSERVED", "重算冲销金额、税额或不含税金额不守恒");
            }
            int updated = jdbc.update("""
                    UPDATE cost_recalculation_batch SET status='REVERSED',updated_by=?,updated_at=CURRENT_TIMESTAMP,
                     version=version+1 WHERE tenant_id=? AND id=? AND status='POSTED'
                    """, userId(), tenantId(), originalId);
            if (updated != 1) throw new BusinessException("COST_RECALCULATION_STATE_INVALID", "原重算批次状态已变化");
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("COST_RECALCULATION_ALREADY_REVERSED", "该重算批次已冲销", ex);
        }
        return reversalId;
    }

    void assertNoActiveOperationalDescendants(Long batchId) {
        List<Map<String, Object>> descendants = jdbc.queryForList("""
                WITH RECURSIVE dependent(root_fact_id,id,amount,tax_amount,amount_without_tax) AS (
                  SELECT root.id,child.id,child.amount,child.tax_amount,child.amount_without_tax
                  FROM cost_item root
                  JOIN cost_item child
                    ON child.tenant_id=root.tenant_id
                   AND child.original_cost_item_id=root.id
                   AND child.deleted_flag=0
                  WHERE root.tenant_id=? AND root.adjustment_batch_id=? AND root.deleted_flag=0
                  UNION ALL
                  SELECT dependent.root_fact_id,child.id,child.amount,child.tax_amount,child.amount_without_tax
                  FROM dependent
                  JOIN cost_item child
                    ON child.tenant_id=?
                   AND child.original_cost_item_id=dependent.id
                   AND child.deleted_flag=0
                )
                SELECT root_fact_id,id,amount,tax_amount,amount_without_tax
                FROM dependent ORDER BY id FOR UPDATE
                """, tenantId(), batchId, tenantId());
        Map<Long, BigDecimal[]> totals = new LinkedHashMap<>();
        for (Map<String, Object> descendant : descendants) {
            BigDecimal[] total = totals.computeIfAbsent(longValue(descendant.get("root_fact_id")), ignored ->
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            total[0] = total[0].add(money(descendant.get("amount")));
            total[1] = total[1].add(money(descendant.get("tax_amount")));
            total[2] = total[2].add(money(descendant.get("amount_without_tax")));
        }
        boolean active = totals.values().stream().anyMatch(total ->
                total[0].signum() != 0 || total[1].signum() != 0 || total[2].signum() != 0);
        if (active) {
            throw new BusinessException("COST_RECALCULATION_DEPENDENT_FACT_ACTIVE",
                    "该批次已有未冲销的后续业务事实，请先完成后续业务冲销");
        }
        List<Long> submittedTransfers = jdbc.queryForList("""
                SELECT request.id
                FROM bid_cost_target_transfer_request request
                JOIN bid_cost_target_transfer_request_line line
                  ON line.tenant_id=request.tenant_id AND line.request_id=request.id
                JOIN cost_item fact
                  ON fact.tenant_id=line.tenant_id AND fact.id=line.source_cost_item_id
                WHERE request.tenant_id=? AND request.status IN('DRAFT','SUBMITTED') AND request.deleted_flag=0
                  AND fact.adjustment_batch_id=? AND fact.deleted_flag=0
                ORDER BY request.id FOR UPDATE
                """, Long.class, tenantId(), batchId);
        if (!submittedTransfers.isEmpty()) {
            throw new BusinessException("COST_RECALCULATION_DEPENDENT_TRANSFER_PENDING",
                    "该批次正被草稿或在途投标成本转入引用，请先取消、撤回或完成后冲销");
        }
        List<Long> activeTransfers = jdbc.queryForList("""
                SELECT original.id
                FROM bid_cost_target_transfer original
                JOIN bid_cost_target_transfer_line line
                  ON line.tenant_id=original.tenant_id AND line.transfer_id=original.id
                JOIN cost_item fact
                  ON fact.tenant_id=line.tenant_id AND fact.id=line.source_cost_item_id
                WHERE original.tenant_id=? AND original.reversal_of_id IS NULL AND original.status='POSTED'
                  AND fact.adjustment_batch_id=? AND fact.deleted_flag=0
                  AND NOT EXISTS(
                    SELECT 1 FROM bid_cost_target_transfer reversal
                    WHERE reversal.tenant_id=original.tenant_id AND reversal.reversal_of_id=original.id
                      AND reversal.status='REVERSED')
                ORDER BY original.id FOR UPDATE
                """, Long.class, tenantId(), batchId);
        if (!activeTransfers.isEmpty()) {
            throw new BusinessException("COST_RECALCULATION_DEPENDENT_TRANSFER_ACTIVE",
                    "该批次已有未冲销的投标成本转入，请先冲销转入事实");
        }
    }

    Map<String, Object> recalculationBatch(Long id) {
        Map<String, Object> result = recalculationBatchForUpdate(id, false);
        Long projectId = longValue(result.get("projectId"));
        if (projectId == null) projectAccessChecker.requireAllScope("查看");
        else requireProject(projectId);
        result.put("lines", jdbc.queryForList("""
                SELECT l.id,l.original_cost_item_id originalCostItemId,l.old_cost_subject_id oldCostSubjectId,
                       os.subject_code oldSubjectCode,os.subject_name oldSubjectName,
                       l.new_cost_subject_id newCostSubjectId,ns.subject_code newSubjectCode,ns.subject_name newSubjectName,
                       l.mapping_version_id mappingVersionId,l.assignment_rule_id assignmentRuleId,l.amount,
                       l.tax_amount taxAmount,l.amount_without_tax amountWithoutTax,l.difference_type differenceType,
                       l.negative_cost_item_id negativeCostItemId,l.positive_cost_item_id positiveCostItemId
                FROM cost_recalculation_line l
                LEFT JOIN cost_subject os ON os.tenant_id=l.tenant_id AND os.id=l.old_cost_subject_id
                LEFT JOIN cost_subject ns ON ns.tenant_id=l.tenant_id AND ns.id=l.new_cost_subject_id
                WHERE l.tenant_id=? AND l.batch_id=? ORDER BY l.id LIMIT 1000
                """, tenantId(), id));
        return result;
    }

    private Map<String, Object> projectConfigRequest(Long id) {
        return one("""
                SELECT id,request_code requestCode,project_id projectId,project_status_snapshot projectStatusSnapshot,
                       base_configuration_version baseConfigurationVersion,
                       direct_apply directApply,status,approval_instance_id approvalInstanceId,applied_at appliedAt,
                       reason,created_by createdBy,created_at createdAt
                FROM cost_project_config_request WHERE tenant_id=? AND id=?
                """, id);
    }

    private Map<String, Object> projectConfigRequestForUpdate(Long id) {
        return one("""
                SELECT id,request_code requestCode,project_id projectId,project_status_snapshot projectStatusSnapshot,
                       base_configuration_version baseConfigurationVersion,
                       direct_apply directApply,status,approval_instance_id approvalInstanceId,reason,created_by createdBy
                FROM cost_project_config_request WHERE tenant_id=? AND id=? FOR UPDATE
                """, id);
    }

    private int currentConfigurationVersion(Long projectId) {
        Integer current = jdbc.queryForObject("""
                SELECT COALESCE(MAX(configuration_version),0) FROM project_cost_subject_scope_history
                WHERE tenant_id=? AND project_id=?
                """, Integer.class, tenantId(), projectId);
        return current == null ? 0 : current;
    }

    private Map<String, Object> impactSnapshot(Long projectId, Long subjectId) {
        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("costFacts", scopedCount("cost_item", projectId, subjectId, "deleted_flag=0"));
        impact.put("targetItems", scopedCount("cost_target_item", projectId, subjectId, "deleted_flag=0"));
        impact.put("budgetLines", scopedCount("project_budget_line", projectId, subjectId, "deleted_flag=0"));
        impact.put("rules", jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_subject_assignment_rule
                WHERE tenant_id=? AND (project_id=? OR project_id IS NULL) AND cost_subject_id=?
                """, Long.class, tenantId(), projectId, subjectId));
        Map<String, Object> project = one("""
                SELECT c.id mainContractId,c.contract_code mainContractCode,c.current_amount mainContractAmount,
                       c.approval_status mainContractApprovalStatus,c.contract_status mainContractStatus,
                       t.version_no targetVersionNo,t.total_target_amount targetAmount
                FROM pm_project p
                LEFT JOIN ct_contract c ON c.tenant_id=p.tenant_id AND c.id=p.owner_contract_id
                  AND c.deleted_flag=0
                LEFT JOIN cost_target t ON t.tenant_id=p.tenant_id AND t.project_id=p.id
                  AND t.status='ACTIVE' AND t.deleted_flag=0
                WHERE p.tenant_id=? AND p.id=? AND p.deleted_flag=0
                ORDER BY t.id DESC LIMIT 1
                """, projectId);
        impact.put("mainContractId", project.get("mainContractId"));
        impact.put("mainContractCode", project.get("mainContractCode"));
        impact.put("mainContractAmount", project.get("mainContractAmount"));
        impact.put("mainContractApprovalStatus", project.get("mainContractApprovalStatus"));
        impact.put("mainContractStatus", project.get("mainContractStatus"));
        impact.put("targetVersionNo", project.get("targetVersionNo"));
        impact.put("targetAmount", project.get("targetAmount"));
        return impact;
    }

    private List<Map<String, Object>> projectConfigLines(Long requestId) {
        return jdbc.queryForList("""
                SELECT l.id,l.cost_subject_id costSubjectId,s.subject_code subjectCode,s.subject_name subjectName,
                       l.enabled,l.effective_from effectiveFrom,l.effective_to effectiveTo,
                       l.impact_snapshot impactSnapshot
                FROM cost_project_config_request_line l
                JOIN cost_subject s ON s.tenant_id=l.tenant_id AND s.id=l.cost_subject_id
                WHERE l.tenant_id=? AND l.request_id=? ORDER BY s.subject_code,l.id
                """, tenantId(), requestId);
    }

    private long scopedCount(String table, Long projectId, Long subjectId, String condition) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                        + " WHERE tenant_id=? AND project_id=? AND cost_subject_id=? AND " + condition,
                Long.class, tenantId(), projectId, subjectId);
        return value == null ? 0 : value;
    }

    private List<Map<String, Object>> loadRecalculationFacts(Long projectId, LocalDateTime cutoff,
                                                              String batchType) {
        String projectClause = projectId == null ? "" : " AND ci.project_id=?";
        String statusClause = "HISTORY_RECALCULATION".equals(batchType) ? " AND p.status<>'CLOSED'" : "";
        List<Object> args = new ArrayList<>();
        args.add(tenantId());
        args.add(java.sql.Date.valueOf(cutoff.toLocalDate()));
        args.addAll(POSTED_ADJUSTMENT_SOURCES);
        if (projectId != null) args.add(projectId);
        return jdbc.queryForList("""
                SELECT ci.id,ci.project_id,ci.wbs_task_id,ci.contract_id,ci.partner_id,ci.cost_subject_id,
                       ci.cost_type,ci.amount,ci.tax_amount,ci.amount_without_tax,ci.source_type,ci.source_id,
                       ci.source_item_id,ci.cost_date,ci.cost_status,ci.classification_status,
                       ci.classification_business_category,
                       ci.recognition_role,COALESCE(ci.root_source_type,ci.source_type) root_source_type,
                       ci.mapping_version_id,ci.assignment_rule_id,ci.original_cost_subject_id,
                       ci.classification_override_id,ci.classification_snapshot_id,ci.adjustment_batch_id,
                       ci.original_cost_item_id,ci.created_at,ci.updated_at
                FROM cost_item ci
                JOIN pm_project p ON p.tenant_id=ci.tenant_id AND p.id=ci.project_id AND p.deleted_flag=0
                LEFT JOIN cost_subject s ON s.tenant_id=ci.tenant_id AND s.id=ci.cost_subject_id
                WHERE ci.tenant_id=? AND ci.deleted_flag=0
                  AND COALESCE(ci.cost_date,CAST(ci.created_at AS DATE))<=?
                  AND ci.cost_status IN ('CONFIRMED','POSTED')
                  AND ci.recognition_role IN ('ACTUAL','COMMITTED')
                  AND ci.source_type NOT IN (?,?)
                  AND (ci.classification_status='UNCLASSIFIED' OR s.account_category='COST')
                  AND NOT EXISTS (
                    SELECT 1 FROM cost_recalculation_batch own_batch
                    WHERE own_batch.tenant_id=ci.tenant_id AND own_batch.id=ci.adjustment_batch_id
                      AND own_batch.status='REVERSED')
                  AND NOT EXISTS (
                    SELECT 1 FROM cost_item successor
                    LEFT JOIN cost_recalculation_batch successor_batch
                      ON successor_batch.tenant_id=successor.tenant_id
                     AND successor_batch.id=successor.adjustment_batch_id
                    WHERE successor.tenant_id=ci.tenant_id AND successor.original_cost_item_id=ci.id
                      AND successor.deleted_flag=0
                      AND (successor.source_type='COST_RECALCULATION_REVERSAL'
                           OR (successor.source_type='COST_RECALCULATION_NEGATIVE'
                               AND successor_batch.status='POSTED')))
                """ + statusClause + projectClause + " ORDER BY ci.id", args.toArray());
    }

    private void insertRecalculationLine(Long batchId, TrialLine line) {
        Map<String, Object> fact = line.fact();
        jdbc.update("""
                INSERT INTO cost_recalculation_line
                (id,tenant_id,batch_id,original_cost_item_id,old_cost_subject_id,new_cost_subject_id,
                 mapping_version_id,assignment_rule_id,amount,tax_amount,amount_without_tax,source_snapshot_hash,difference_type)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, IdWorker.getId(), tenantId(), batchId, longValue(fact.get("id")),
                longValue(fact.get("cost_subject_id")), line.decision() == null ? null : line.decision().costSubjectId(),
                line.decision() == null ? null : line.decision().mappingVersionId(),
                line.decision() == null ? null : line.decision().assignmentRuleId(),
                money(fact.get("amount")), money(fact.get("tax_amount")), money(fact.get("amount_without_tax")),
                line.factHash(), line.difference());
    }

    private Map<String, Object> recalculationBatchForUpdate(Long id) {
        return recalculationBatchForUpdate(id, true);
    }

    private Map<String, Object> recalculationBatchForUpdate(Long id, boolean lock) {
        return one("""
                SELECT id,batch_code batchCode,batch_type batchType,project_id projectId,scope_key scopeKey,
                       cutoff_at cutoffAt,source_snapshot_hash sourceSnapshotHash,idempotency_key idempotencyKey,
                       rule_version_id ruleVersionId,status,original_fact_count originalFactCount,
                       changed_fact_count changedFactCount,unclassified_count unclassifiedCount,
                       original_total originalTotal,adjustment_total adjustmentTotal,old_snapshot oldSnapshot,
                       difference_report differenceReport,approval_instance_id approvalInstanceId,
                       posted_at postedAt,created_by createdBy,reason,created_at createdAt
                FROM cost_recalculation_batch WHERE tenant_id=? AND id=?
                """ + (lock ? " FOR UPDATE" : ""), id);
    }

    private void authorizeRecalculationBatch(Map<String, Object> batch, String action) {
        Long projectId = longValue(batch.get("projectId"));
        if (projectId == null) projectAccessChecker.requireAllScope(action + "全公司成本重算");
        else requireProject(projectId);
    }

    private void requireRecalculationProjectState(String batchType, Long projectId) {
        if ("POST_CLOSE_ADJUSTMENT".equals(batchType) && projectId == null) {
            throw new BusinessException("COST_POST_CLOSE_PROJECT_REQUIRED", "关闭后调整必须选择项目");
        }
        if (projectId == null) return;
        String status = jdbc.queryForObject("""
                SELECT status FROM pm_project WHERE tenant_id=? AND id=? AND deleted_flag=0
                """, String.class, tenantId(), projectId);
        if ("POST_CLOSE_ADJUSTMENT".equals(batchType) && !"CLOSED".equals(status)) {
            throw new BusinessException("COST_POST_CLOSE_PROJECT_NOT_CLOSED", "仅已关闭项目可发起或过账关闭后调整");
        }
        if ("HISTORY_RECALCULATION".equals(batchType) && "CLOSED".equals(status)) {
            throw new BusinessException("COST_HISTORY_RECALCULATION_PROJECT_CLOSED",
                    "已关闭项目只读；请使用关闭后财务调整流程");
        }
    }

    private void verifyRecalculationSnapshot(Map<String, Object> batch) {
        Timestamp timestamp = (Timestamp) batch.get("cutoffAt");
        List<Map<String, Object>> facts = loadRecalculationFacts(longValue(batch.get("projectId")),
                timestamp.toLocalDateTime(), String.valueOf(batch.get("batchType")));
        List<SnapshotEntry> snapshots = facts.stream().map(fact -> new SnapshotEntry(
                longValue(fact.get("id")), factHash(fact),
                Objects.toString(fact.get("cost_subject_id"), ""))).toList();
        String currentHash = sha256(json(snapshots));
        if (!Objects.equals(currentHash, String.valueOf(batch.get("sourceSnapshotHash")))) {
            throw new BusinessException("COST_RECALCULATION_SOURCE_DRIFT", "冻结基准后的历史事实已变化，请重新试算");
        }
    }

    private void reserveRecalculationFacts(Long batchId) {
        List<Long> factIds = jdbc.queryForList("""
                SELECT original_cost_item_id FROM cost_recalculation_line
                WHERE tenant_id=? AND batch_id=? ORDER BY original_cost_item_id
                """, Long.class, tenantId(), batchId);
        try {
            for (Long factId : factIds) {
                jdbc.update("""
                        INSERT INTO cost_recalculation_fact_reservation
                        (id,tenant_id,batch_id,original_cost_item_id) VALUES (?,?,?,?)
                        """, IdWorker.getId(), tenantId(), batchId, factId);
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("COST_RECALCULATION_FACT_BUSY", "同一历史事实已被其他审批中重算批次占用", ex);
        }
    }

    private void assertNoCompetingRecalculation(Map<String, Object> batch, Long batchId) {
        List<Long> scopeLock = jdbc.queryForList("""
                SELECT id FROM cost_recalculation_batch
                WHERE tenant_id=? AND batch_type=? AND scope_key=?
                ORDER BY id LIMIT 1 FOR UPDATE
                """, Long.class, tenantId(), batch.get("batchType"), batch.get("scopeKey"));
        if (scopeLock.isEmpty()) {
            throw new BusinessException("COST_RECALCULATION_NOT_FOUND", "重算批次不存在");
        }
        Integer competing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_recalculation_batch
                WHERE tenant_id=? AND batch_type=? AND scope_key=? AND id<>?
                  AND status IN('DRAFT','SUBMITTED')
                """, Integer.class, tenantId(), batch.get("batchType"), batch.get("scopeKey"), batchId);
        if (competing != null && competing > 0) {
            throw new BusinessException("COST_RECALCULATION_ACTIVE_CONFLICT", "同一范围已有活动重算批次");
        }
    }

    private void lockRecalculationFacts(Long batchId) {
        Integer expected = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_recalculation_line WHERE tenant_id=? AND batch_id=?
                """, Integer.class, tenantId(), batchId);
        List<Long> locked = jdbc.queryForList("""
                SELECT ci.id FROM cost_item ci
                JOIN cost_recalculation_line line
                  ON line.tenant_id=ci.tenant_id AND line.original_cost_item_id=ci.id
                WHERE line.tenant_id=? AND line.batch_id=? AND ci.deleted_flag=0
                ORDER BY ci.id FOR UPDATE
                """, Long.class, tenantId(), batchId);
        if (expected == null || locked.size() != expected) {
            throw new BusinessException("COST_RECALCULATION_SOURCE_DRIFT", "冻结基准后的历史事实已变化，请重新试算");
        }
    }

    private Map<String, Object> costFactForUpdate(Long id) {
        return one("""
                SELECT id,project_id,wbs_task_id,contract_id,partner_id,cost_subject_id,cost_type,amount,tax_amount,
                       amount_without_tax,cost_date,cost_status,source_type,source_id,source_item_id,
                       COALESCE(root_source_type,source_type) root_source_type,
                       mapping_version_id,assignment_rule_id,original_cost_subject_id,
                       classification_override_id,classification_snapshot_id,classification_status,
                       classification_business_category,recognition_role,
                       adjustment_batch_id,original_cost_item_id,created_at,updated_at
                FROM cost_item WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE
                """, id);
    }

    private void lockRecalculationPostingSubjects(List<Map<String, Object>> lines) {
        List<Long> oldSubjectIds = lines.stream().map(line -> longValue(line.get("old_cost_subject_id")))
                .filter(Objects::nonNull).distinct().sorted().toList();
        List<Long> newSubjectIds = lines.stream().map(line -> longValue(line.get("new_cost_subject_id")))
                .filter(Objects::nonNull).distinct().sorted().toList();
        List<Long> subjectIds = java.util.stream.Stream.concat(oldSubjectIds.stream(), newSubjectIds.stream())
                .distinct().sorted().toList();
        Map<Long, Map<String, Object>> locked = lockHistoricalCostSubjects(subjectIds).stream()
                .collect(java.util.stream.Collectors.toMap(row -> longValue(row.get("id")), row -> row));
        for (Long subjectId : newSubjectIds) {
            Map<String, Object> subject = locked.get(subjectId);
            Integer children = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM cost_subject
                    WHERE tenant_id=? AND parent_id=? AND deleted_flag=0
                    """, Integer.class, tenantId(), subjectId);
            if (!"ENABLE".equals(String.valueOf(subject.get("status"))) || children == null || children != 0) {
                throw new BusinessException("COST_SUBJECT_NOT_LEAF", "重算目标必须是启用的成本域末级科目");
            }
            Integer disabledRules = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM overhead_allocation_rule
                    WHERE tenant_id=? AND cost_subject_id=? AND status='DISABLE' AND deleted_flag=0
                    """, Integer.class, tenantId(), subjectId);
            if (disabledRules != null && disabledRules > 0) {
                throw new BusinessException("OVERHEAD_RULE_DISABLED_FOR_COST",
                        "重算涉及已停用的间接费科目，重新启用分摊规则后方可过账");
            }
        }
        for (Map<String, Object> line : lines) {
            requireScope(longValue(line.get("original_project_id")), longValue(line.get("new_cost_subject_id")));
        }
    }

    private List<Map<String, Object>> lockHistoricalCostSubjects(List<Long> subjectIds) {
        List<Long> ids = subjectIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>();
        args.add(tenantId());
        args.addAll(ids);
        List<Map<String, Object>> subjects = jdbc.queryForList("""
                SELECT id,status,account_category FROM cost_subject
                WHERE tenant_id=? AND id IN (%s) AND deleted_flag=0 ORDER BY id FOR UPDATE
                """.formatted(placeholders(ids)), args.toArray());
        if (subjects.size() != ids.size() || subjects.stream()
                .anyMatch(subject -> !"COST".equals(String.valueOf(subject.get("account_category"))))) {
            throw new BusinessException("COST_SUBJECT_HISTORY_INVALID", "历史重算引用的成本科目不存在或已离开成本域");
        }
        return subjects;
    }

    private List<Long> recalculationProjectIds(Long batchId) {
        return jdbc.queryForList("""
                SELECT DISTINCT fact.project_id
                FROM cost_recalculation_line line
                JOIN cost_item fact ON fact.tenant_id=line.tenant_id
                  AND fact.id=line.original_cost_item_id AND fact.deleted_flag=0
                WHERE line.tenant_id=? AND line.batch_id=? ORDER BY fact.project_id
                """, Long.class, tenantId(), batchId);
    }

    private void assertRecalculationPeriodsWritable(Long batchId) {
        List<LocalDate> dates = jdbc.query("""
                SELECT DISTINCT fact.cost_date
                FROM cost_recalculation_line line
                JOIN cost_item fact ON fact.tenant_id=line.tenant_id
                  AND fact.id=line.original_cost_item_id AND fact.deleted_flag=0
                WHERE line.tenant_id=? AND line.batch_id=? ORDER BY fact.cost_date
                """, (rs, rowNum) -> rs.getObject(1, LocalDate.class), tenantId(), batchId);
        accountingPeriodGuard.assertWritable(dates.toArray(LocalDate[]::new));
    }

    private void lockAndValidateRecalculationProjects(String batchType, List<Long> projectIds) {
        List<Long> ids = projectIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) return;
        List<Object> args = new ArrayList<>();
        args.add(tenantId());
        args.addAll(ids);
        List<Map<String, Object>> projects = jdbc.queryForList("""
                SELECT id,status FROM pm_project
                WHERE tenant_id=? AND id IN (%s) AND deleted_flag=0 ORDER BY id FOR UPDATE
                """.formatted(placeholders(ids)), args.toArray());
        if (projects.size() != ids.size()) {
            throw new BusinessException("COST_RECALCULATION_PROJECT_INVALID", "历史重算包含不存在或已失效项目");
        }
        for (Map<String, Object> project : projects) {
            String status = String.valueOf(project.get("status"));
            if ("HISTORY_RECALCULATION".equals(batchType) && "CLOSED".equals(status)) {
                throw new BusinessException("COST_HISTORY_RECALCULATION_PROJECT_CLOSED",
                        "全公司历史重算包含已关闭项目；请改用关闭后财务调整流程");
            }
            if ("POST_CLOSE_ADJUSTMENT".equals(batchType) && !"CLOSED".equals(status)) {
                throw new BusinessException("COST_POST_CLOSE_PROJECT_NOT_CLOSED",
                        "关闭后财务调整仅允许已关闭项目");
            }
        }
    }

    private void requireNoPendingOverheadFacts(Long batchId) {
        Integer reclassifiedOverhead = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM cost_recalculation_line line
                JOIN overhead_allocation_rule rule
                  ON rule.tenant_id=line.tenant_id
                 AND rule.cost_subject_id IN (line.old_cost_subject_id,line.new_cost_subject_id)
                 AND rule.deleted_flag=0
                WHERE line.tenant_id=? AND line.batch_id=? AND line.difference_type='RECLASSIFY'
                """, Integer.class, tenantId(), batchId);
        if (reclassifiedOverhead != null && reclassifiedOverhead > 0) {
            throw new BusinessException("COST_RECALCULATION_OVERHEAD_SUBJECT_UNSUPPORTED",
                    "历史重算暂不允许移入或移出间接费分摊科目；请先按原规则完成分摊并使用普通成本科目重分类");
        }
        Integer pending = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM cost_recalculation_line line
                JOIN cost_item fact ON fact.tenant_id=line.tenant_id AND fact.id=line.original_cost_item_id
                JOIN overhead_allocation_rule rule
                  ON rule.tenant_id=fact.tenant_id AND rule.cost_subject_id=fact.cost_subject_id
                 AND rule.allocation_cycle='MONTHLY' AND rule.deleted_flag=0
                WHERE line.tenant_id=? AND line.batch_id=?
                  AND fact.deleted_flag=0 AND fact.cost_status IN ('CONFIRMED','POSTED')
                  AND fact.classification_status<>'UNCLASSIFIED' AND fact.recognition_role='ACTUAL'
                  AND fact.source_type NOT IN ('OVERHEAD_ALLOCATION','OVERHEAD_ALLOCATION_CLEARING',
                    'COST_RECALCULATION_NEGATIVE','COST_RECALCULATION_POSITIVE','COST_RECALCULATION_REVERSAL')
                  AND NOT EXISTS (
                    SELECT 1 FROM cost_item clearing
                    WHERE clearing.tenant_id=fact.tenant_id AND clearing.original_cost_item_id=fact.id
                      AND clearing.source_type='OVERHEAD_ALLOCATION_CLEARING'
                      AND clearing.deleted_flag=0 AND clearing.cost_status<>'WRITE_OFF')
                """, Integer.class, tenantId(), batchId);
        if (pending != null && pending > 0) {
            throw new BusinessException("COST_RECALCULATION_OVERHEAD_PENDING",
                    "历史重算包含尚未完成月度分摊的间接费事实，请先完成原期间分摊");
        }
    }

    private Long insertAdjustmentFact(Map<String, Object> batch, Map<String, Object> line,
                                      Map<String, Object> original, boolean negative) {
        Long id = IdWorker.getId();
        BigDecimal sign = negative ? BigDecimal.ONE.negate() : BigDecimal.ONE;
        Long subjectId = negative ? longValue(line.get("old_cost_subject_id")) : longValue(line.get("new_cost_subject_id"));
        Object mappingVersionId = negative ? original.get("mapping_version_id") : line.get("mapping_version_id");
        Object assignmentRuleId = negative ? original.get("assignment_rule_id") : line.get("assignment_rule_id");
        Object originalSubjectId = negative ? original.get("original_cost_subject_id") : line.get("old_cost_subject_id");
        Object overrideId = negative ? original.get("classification_override_id") : null;
        Object snapshotId = negative ? original.get("classification_snapshot_id") : null;
        String classificationStatus = negative ? "REVERSAL" : "ADJUSTMENT";
        String costType = String.valueOf(original.get("cost_type"));
        String sourceType = negative ? "COST_RECALCULATION_NEGATIVE" : "COST_RECALCULATION_POSITIVE";
        String rootSourceType = String.valueOf(original.get("root_source_type"));
        Object sourceId = batch.get("id");
        Object sourceItemId = line.get("id");
        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,wbs_task_id,contract_id,partner_id,cost_subject_id,classification_status,
                 classification_business_category,recognition_role,
                 root_source_type,mapping_version_id,assignment_rule_id,original_cost_subject_id,classification_override_id,
                 classification_snapshot_id,adjustment_batch_id,original_cost_item_id,cost_type,amount,tax_amount,
                 amount_without_tax,source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,created_by,remark)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, tenantId(), original.get("project_id"), original.get("wbs_task_id"),
                original.get("contract_id"), original.get("partner_id"), subjectId, classificationStatus,
                original.get("classification_business_category"), original.get("recognition_role"), rootSourceType,
                mappingVersionId, assignmentRuleId, originalSubjectId,
                overrideId, snapshotId, batch.get("id"),
                original.get("id"), costType,
                money(line.get("amount")).multiply(sign), money(line.get("tax_amount")).multiply(sign),
                money(line.get("amount_without_tax")).multiply(sign),
                sourceType, sourceId, sourceItemId, original.get("cost_date"), "CONFIRMED", 1, userId(),
                "历史重算批次 " + batch.get("batchCode") + (negative ? " 冲销原科目" : " 转入新科目"));
        return id;
    }

    private String factHash(Map<String, Object> fact) {
        return sha256(String.join("|",
                Objects.toString(fact.get("id"), ""), Objects.toString(fact.get("project_id"), ""),
                Objects.toString(fact.get("wbs_task_id"), ""), Objects.toString(fact.get("contract_id"), ""),
                Objects.toString(fact.get("partner_id"), ""),
                Objects.toString(fact.get("cost_subject_id"), ""), Objects.toString(fact.get("amount"), ""),
                Objects.toString(fact.get("tax_amount"), ""), Objects.toString(fact.get("amount_without_tax"), ""),
                Objects.toString(fact.get("cost_type"), ""), Objects.toString(fact.get("recognition_role"), ""),
                Objects.toString(fact.get("root_source_type"), ""),
                Objects.toString(fact.get("source_type"), ""), Objects.toString(fact.get("source_id"), ""),
                Objects.toString(fact.get("source_item_id"), ""), Objects.toString(fact.get("cost_date"), ""),
                Objects.toString(fact.get("cost_status"), ""), Objects.toString(fact.get("classification_status"), ""),
                Objects.toString(fact.get("classification_business_category"), ""),
                Objects.toString(fact.get("mapping_version_id"), ""), Objects.toString(fact.get("assignment_rule_id"), ""),
                Objects.toString(fact.get("original_cost_subject_id"), ""),
                Objects.toString(fact.get("classification_override_id"), ""),
                Objects.toString(fact.get("classification_snapshot_id"), ""),
                Objects.toString(fact.get("adjustment_batch_id"), ""),
                Objects.toString(fact.get("original_cost_item_id"), ""),
                Objects.toString(fact.get("created_at"), ""), Objects.toString(fact.get("updated_at"), "")));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("成本治理快照序列化失败", ex);
        }
    }

    private static String tail(Long value) {
        String text = String.valueOf(value);
        return text.substring(Math.max(0, text.length() - 8));
    }

    private static LocalDate dateValue(Object value, Object fallback) {
        Object selected = value == null ? fallback : value;
        if (selected instanceof LocalDate date) return date;
        if (selected instanceof java.sql.Date date) return date.toLocalDate();
        if (selected instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        if (selected instanceof LocalDateTime dateTime) return dateTime.toLocalDate();
        return LocalDate.parse(String.valueOf(selected).substring(0, 10));
    }

    private record TrialLine(Map<String, Object> fact, CostSubjectResolver.Decision decision,
                             String difference, String factHash) {}

    private record SnapshotEntry(Long id, String hash, String subjectId) {}
}
