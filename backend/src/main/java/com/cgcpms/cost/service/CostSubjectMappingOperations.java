package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.service.CostSubjectV2Service.MappingItem;
import com.cgcpms.cost.service.CostSubjectV2Service.MappingRule;
import com.cgcpms.cost.service.CostSubjectV2Service.MappingVersionCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.RuleCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.ScopeCommand;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CostSubjectMappingOperations extends CostSubjectV2Support {
    private static final List<String> REQUIRED_SOURCE_TYPES = List.of(
            "MAT_RECEIPT", "MAT_REQUISITION", "SUB_MEASURE", "VAR_ORDER", "CT_CHANGE", "CT_CONTRACT",
            "QUALITY_SAFETY_CONSEQUENCE", "OVERHEAD_ALLOCATION", "OVERHEAD_ALLOCATION_CLEARING",
            "ACCOUNTING_ENTRY_LINE", "EXPENSE_APPLICATION", "FINANCE_COST_ALLOCATION", "FINANCE_COST_ALLOCATION_REVERSAL",
            "BID_COST", "BID_COST_WRITE_OFF", "MATERIAL_RETURN", "MATERIAL_RETURN_REVERSAL",
            "SUPPLIER_RETURN", "SUPPLIER_RETURN_REVERSAL");

    CostSubjectMappingOperations(JdbcTemplate jdbc, ProjectAccessChecker projectAccessChecker) {
        super(jdbc, projectAccessChecker);
    }

    List<Map<String, Object>> mappingVersions() {
        return jdbc.queryForList("""
                SELECT v.id,v.version_code,v.version_name,v.status,v.effective_date,v.approval_instance_id,
                       v.activated_by,v.activated_at,v.created_at,v.remark,COUNT(i.id) item_count
                FROM cost_subject_mapping_version v
                LEFT JOIN cost_subject_mapping_item i ON i.mapping_version_id=v.id AND i.tenant_id=v.tenant_id
                WHERE v.tenant_id=?
                GROUP BY v.id,v.version_code,v.version_name,v.status,v.effective_date,v.approval_instance_id,
                         v.activated_by,v.activated_at,v.created_at,v.remark
                ORDER BY v.created_at DESC
                """, tenantId());
    }

    List<Map<String, Object>> mappingItems(Long versionId) {
        return jdbc.queryForList("""
                SELECT i.id,i.source_subject_id,s.subject_code source_subject_code,s.subject_name source_subject_name,
                       i.target_group_code,i.target_subject_id,t.subject_code target_subject_code,
                       t.subject_name target_subject_name,i.historical_display_name,i.mapping_reason
                FROM cost_subject_mapping_item i
                JOIN cost_subject s ON s.id=i.source_subject_id AND s.tenant_id=i.tenant_id
                LEFT JOIN cost_subject t ON t.id=i.target_subject_id AND t.tenant_id=i.tenant_id
                WHERE i.tenant_id=? AND i.mapping_version_id=? ORDER BY s.subject_code
                """, tenantId(), versionId);
    }

    Map<String, Object> mappingVersionDetail(Long versionId) {
        Map<String, Object> main = one("""
                SELECT v.id,v.version_code versionCode,v.version_name versionName,v.status,
                 v.effective_date effectiveDate,v.approval_instance_id approvalInstanceId,
                 v.validated_at validatedAt,v.validation_report validationReport,
                 v.created_by createdBy,u.real_name activatedByName,v.activated_at activatedAt,v.created_at createdAt,v.remark
                FROM cost_subject_mapping_version v
                LEFT JOIN sys_user u ON u.id=v.activated_by AND u.tenant_id=v.tenant_id AND u.deleted_flag=0
                WHERE v.tenant_id=? AND v.id=?
                """, versionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("main", main);
        result.put("items", mappingItems(versionId));
        result.put("rules", rules(versionId));
        return result;
    }

    void requireRulePlanCreator(Long id) {
        Map<String, Object> version = one("""
                SELECT created_by createdBy FROM cost_subject_mapping_version
                WHERE tenant_id=? AND id=? FOR UPDATE
                """, id);
        requireCurrentUserCreated(version.get("createdBy"), "成本规则方案");
    }

    Long createMappingVersion(MappingVersionCommand command) {
        requireText(command.versionCode(), "映射版本编码不能为空");
        requireText(command.versionName(), "映射版本名称不能为空");
        if (command.effectiveDate() == null) {
            throw new BusinessException("COST_RULE_PLAN_EFFECTIVE_DATE_REQUIRED", "方案生效日期不能为空");
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new BusinessException("COST_SUBJECT_MAPPING_EMPTY", "映射版本至少包含一条科目映射");
        }
        if (command.items().size() > APPROVAL_DETAIL_ROW_LIMIT) {
            throw new BusinessException("COST_RULE_PLAN_MAPPING_LIMIT_EXCEEDED", "单个成本规则方案最多维护1000条科目映射");
        }
        if (command.rules() != null && command.rules().size() > APPROVAL_DETAIL_ROW_LIMIT) {
            throw new BusinessException("COST_RULE_PLAN_RULE_LIMIT_EXCEEDED", "单个成本规则方案最多维护1000条归集规则");
        }
        requireLeafSubjects(command.items().stream()
                .flatMap(item -> java.util.stream.Stream.of(item.sourceSubjectId(), item.targetSubjectId()))
                .toList());
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO cost_subject_mapping_version
                    (id,tenant_id,version_code,version_name,status,effective_date,created_by,remark)
                    VALUES (?,?,?,?, 'DRAFT',?,?,?)
                    """, id, tenantId(), command.versionCode().trim(), command.versionName().trim(),
                    command.effectiveDate(), userId(), command.remark());
            for (MappingItem item : command.items()) {
                requireText(item.targetGroupCode(), "V2归集组不能为空");
                requireText(item.historicalDisplayName(), "历史展示名称不能为空");
                jdbc.update("""
                        INSERT INTO cost_subject_mapping_item
                        (id,tenant_id,mapping_version_id,source_subject_id,target_group_code,target_subject_id,
                         historical_display_name,mapping_reason,created_by)
                        VALUES (?,?,?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), id, item.sourceSubjectId(),
                        item.targetGroupCode().trim(), item.targetSubjectId(), item.historicalDisplayName().trim(),
                        item.mappingReason(), userId());
            }
            if (command.rules() != null) {
                for (MappingRule rule : command.rules()) insertRule(id, rule);
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("COST_SUBJECT_MAPPING_DUPLICATE", "版本编码或源科目映射重复", ex);
        }
        return id;
    }

    Map<String, Object> generateInitialPlan() {
        List<Map<String, Object>> subjects = jdbc.queryForList("""
                SELECT s.id,s.subject_code,s.subject_name,s.subject_type
                FROM cost_subject s
                WHERE s.tenant_id=? AND s.deleted_flag=0 AND s.status='ENABLE' AND s.account_category='COST'
                  AND NOT EXISTS (SELECT 1 FROM cost_subject c WHERE c.tenant_id=s.tenant_id
                                  AND c.parent_id=s.id AND c.deleted_flag=0)
                ORDER BY s.subject_code,s.id
                """, tenantId());
        if (subjects.isEmpty()) throw new BusinessException("COST_RULE_TEMPLATE_SUBJECTS_EMPTY", "企业没有启用的成本末级科目");
        String generatedId = String.valueOf(IdWorker.getId());
        String suffix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + generatedId.substring(Math.max(0, generatedId.length() - 6));
        List<MappingItem> items = subjects.stream().map(row -> new MappingItem(
                longValue(row.get("id")), textOrDefault(String.valueOf(row.get("subject_type")), "OTHER"),
                longValue(row.get("id")), String.valueOf(row.get("subject_name")), "标准初始方案同口径映射")).toList();
        List<MappingRule> rules = new ArrayList<>();
        addTemplateRule(rules, subjects, "MAT_RECEIPT", "材料", "MATERIAL", suffix);
        addTemplateRule(rules, subjects, "MAT_REQUISITION", "材料", "MATERIAL", suffix);
        addTemplateRule(rules, subjects, "SUB_MEASURE", "分包", "SUBCONTRACT", suffix);
        addTemplateRule(rules, subjects, "VAR_ORDER", "变更", "VARIATION", suffix);
        addTemplateRule(rules, subjects, "CT_CHANGE", "变更", "VARIATION", suffix);
        addTemplateRule(rules, subjects, "CT_CONTRACT", "合同", "CONTRACT", suffix);
        addTemplateRule(rules, subjects, "QUALITY_SAFETY_CONSEQUENCE", "其他成本", "QUALITY", suffix);
        addTemplateRule(rules, subjects, "OVERHEAD_ALLOCATION", "间接费用", "OVERHEAD", suffix);
        addTemplateRule(rules, subjects, "OVERHEAD_ALLOCATION_CLEARING", "间接费用", "OVERHEAD", suffix);
        addTemplateRule(rules, subjects, "ACCOUNTING_ENTRY_LINE", "财务及税费", "FINANCE", suffix);
        addTemplateRule(rules, subjects, "EXPENSE_APPLICATION", "财务及税费", "FINANCE", suffix);
        addTemplateRule(rules, subjects, "FINANCE_COST_ALLOCATION", "财务及税费", "FINANCE", suffix);
        addTemplateRule(rules, subjects, "FINANCE_COST_ALLOCATION_REVERSAL", "财务及税费", "FINANCE", suffix);
        addTemplateRule(rules, subjects, "BID_COST", "财务及税费", "FINANCE", suffix);
        addTemplateRule(rules, subjects, "BID_COST_WRITE_OFF", "财务及税费", "FINANCE", suffix);
        addTemplateRule(rules, subjects, "MATERIAL_RETURN", "材料", "MATERIAL", suffix);
        addTemplateRule(rules, subjects, "MATERIAL_RETURN_REVERSAL", "材料", "MATERIAL", suffix);
        addTemplateRule(rules, subjects, "SUPPLIER_RETURN", "材料", "MATERIAL", suffix);
        addTemplateRule(rules, subjects, "SUPPLIER_RETURN_REVERSAL", "材料", "MATERIAL", suffix);
        Long id = createMappingVersion(new MappingVersionCommand(
                "CRP-" + suffix, "标准初始成本规则方案", LocalDate.now(),
                "系统按当前成本末级科目和权威业务来源生成；须人工复核、校验并审批后启用", items, rules));
        Map<String, Object> result = mappingVersionDetail(id);
        result.put("generatedRuleCount", rules.size());
        result.put("autoActivated", false);
        return result;
    }

    Map<String, Object> validateMappingVersion(Long id) {
        Map<String, Object> main = one("""
                SELECT id,status,approval_instance_id FROM cost_subject_mapping_version
                WHERE tenant_id=? AND id=? FOR UPDATE
                """, id);
        if (!List.of("DRAFT", "REJECTED", "VALIDATED").contains(String.valueOf(main.get("status")))) {
            throw new BusinessException("COST_RULE_PLAN_NOT_VALIDATABLE", "仅草稿、驳回或已校验方案可以重新校验");
        }
        LocalDate effectiveDate = jdbc.queryForObject("""
                SELECT effective_date FROM cost_subject_mapping_version WHERE tenant_id=? AND id=?
                """, LocalDate.class, tenantId(), id);
        Map<String, Object> report = buildValidationReport(id, effectiveDate);
        boolean passed = Boolean.TRUE.equals(report.get("passed"));
        jdbc.update("""
                UPDATE cost_subject_mapping_version
                SET status=?,validated_by=?,validated_at=CURRENT_TIMESTAMP,validation_report=?,
                    updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=?
                """, passed ? "VALIDATED" : "DRAFT", userId(), report.toString(), userId(), tenantId(), id);
        return report;
    }

    private Map<String, Object> buildValidationReport(Long id, LocalDate asOf) {
        Integer itemCount = jdbc.queryForObject("SELECT COUNT(*) FROM cost_subject_mapping_item WHERE tenant_id=? AND mapping_version_id=?",
                Integer.class, tenantId(), id);
        Integer ruleCount = jdbc.queryForObject("SELECT COUNT(*) FROM cost_subject_assignment_rule WHERE tenant_id=? AND mapping_version_id=?",
                Integer.class, tenantId(), id);
        Integer invalidSubjects = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT i.source_subject_id subject_id FROM cost_subject_mapping_item i WHERE i.tenant_id=? AND i.mapping_version_id=?
                  UNION ALL SELECT i.target_subject_id FROM cost_subject_mapping_item i WHERE i.tenant_id=? AND i.mapping_version_id=? AND i.target_subject_id IS NOT NULL
                  UNION ALL SELECT r.cost_subject_id FROM cost_subject_assignment_rule r WHERE r.tenant_id=? AND r.mapping_version_id=?
                ) x LEFT JOIN cost_subject s ON s.tenant_id=? AND s.id=x.subject_id
                WHERE s.id IS NULL OR s.deleted_flag<>0 OR s.status<>'ENABLE' OR s.account_category<>'COST'
                  OR EXISTS (SELECT 1 FROM cost_subject c WHERE c.tenant_id=s.tenant_id AND c.parent_id=s.id AND c.deleted_flag=0)
                """, Integer.class, tenantId(), id, tenantId(), id, tenantId(), id, tenantId());
        List<Map<String, Object>> conflicts = jdbc.queryForList("""
                SELECT source_type,business_category,COALESCE(project_id,0) project_id,priority,COUNT(*) conflict_count
                FROM cost_subject_assignment_rule
                WHERE tenant_id=? AND mapping_version_id=?
                  AND effective_from<=? AND (effective_to IS NULL OR effective_to>=?)
                GROUP BY source_type,business_category,COALESCE(project_id,0),priority HAVING COUNT(*)>1
                ORDER BY source_type,business_category,project_id,priority
                """, tenantId(), id, asOf, asOf);
        List<String> configured = jdbc.queryForList("""
                SELECT DISTINCT source_type FROM cost_subject_assignment_rule
                WHERE tenant_id=? AND mapping_version_id=?
                  AND project_id IS NULL AND business_category='*'
                  AND effective_from<=? AND (effective_to IS NULL OR effective_to>=?)
                """, String.class, tenantId(), id, asOf, asOf);
        List<String> missing = REQUIRED_SOURCE_TYPES.stream().filter(type -> !configured.contains(type)).toList();
        boolean passed = itemCount != null && itemCount > 0 && ruleCount != null && ruleCount > 0
                && invalidSubjects != null && invalidSubjects == 0 && conflicts.isEmpty() && missing.isEmpty();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", passed);
        report.put("itemCount", itemCount == null ? 0 : itemCount);
        report.put("ruleCount", ruleCount == null ? 0 : ruleCount);
        report.put("invalidSubjectCount", invalidSubjects == null ? 0 : invalidSubjects);
        report.put("conflicts", conflicts);
        report.put("missingSourceTypes", missing);
        return report;
    }

    Map<String, Object> mappingVersionDiff(Long id, Long baseId) {
        requireMappingVersionAnyStatus(id);
        requireMappingVersionAnyStatus(baseId);
        Set<String> current = planRows(id);
        Set<String> base = planRows(baseId);
        Set<String> added = new HashSet<>(current);
        added.removeAll(base);
        Set<String> removed = new HashSet<>(base);
        removed.removeAll(current);
        return Map.of("added", added.stream().sorted().toList(), "removed", removed.stream().sorted().toList(),
                "addedCount", added.size(), "removedCount", removed.size());
    }

    Map<String, Object> trialMappingVersion(Long id, String sourceType, String businessCategory, Long projectId) {
        requireMappingVersionAnyStatus(id);
        if (projectId != null) requireProject(projectId);
        requireText(sourceType, "业务来源不能为空");
        String category = textOrDefault(businessCategory, "*");
        LocalDate trialDate = jdbc.queryForObject("""
                SELECT effective_date FROM cost_subject_mapping_version
                WHERE tenant_id=? AND id=?
                """, LocalDate.class, tenantId(), id);
        List<Map<String, Object>> matches = jdbc.queryForList("""
                SELECT r.id ruleId,r.rule_code ruleCode,r.cost_subject_id costSubjectId,
                       s.subject_code subjectCode,s.subject_name subjectName,r.priority,
                       CASE WHEN r.project_id IS NOT NULL THEN 0 ELSE 1 END projectRank,
                       CASE WHEN r.business_category=? THEN 0 ELSE 1 END categoryRank
                FROM cost_subject_assignment_rule r JOIN cost_subject s ON s.tenant_id=r.tenant_id AND s.id=r.cost_subject_id
                WHERE r.tenant_id=? AND r.mapping_version_id=? AND r.source_type=?
                  AND r.business_category IN (?, '*') AND (r.project_id=? OR r.project_id IS NULL)
                  AND r.effective_from<=? AND (r.effective_to IS NULL OR r.effective_to>=?)
                ORDER BY projectRank,categoryRank,r.priority,r.id LIMIT 2
                """, category, tenantId(), id, sourceType.trim(), category, projectId, trialDate, trialDate);
        if (matches.isEmpty()) return Map.of("matched", false, "reason", "UNCLASSIFIED", "trialDate", trialDate);
        boolean conflict = matches.size() > 1 && intValue(matches.get(0).get("projectRank")) == intValue(matches.get(1).get("projectRank"))
                && intValue(matches.get(0).get("categoryRank")) == intValue(matches.get(1).get("categoryRank"))
                && intValue(matches.get(0).get("priority")) == intValue(matches.get(1).get("priority"));
        if (conflict) return Map.of("matched", false, "reason", "AMBIGUOUS", "candidates", matches);
        Map<String, Object> result = new LinkedHashMap<>(matches.getFirst());
        if (projectId != null) {
            requireScopeAt(projectId, longValue(result.get("costSubjectId")), trialDate);
        }
        result.put("matched", true);
        result.put("trialDate", trialDate);
        return result;
    }

    void activateMappingVersion(Long id, Long approvalInstanceId) {
        throw new BusinessException("COST_RULE_PLAN_APPROVAL_REQUIRED", "成本规则方案只能经系统校验和财务负责人审批后自动启用");
    }

    void markRulePlanSubmitted(Long id, Long instanceId) {
        int updated = jdbc.update("""
                UPDATE cost_subject_mapping_version
                SET status='SUBMITTED',approval_instance_id=?,submitted_by=?,submitted_at=CURRENT_TIMESTAMP,
                    updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status='VALIDATED'
                  AND effective_date<=CURRENT_DATE
                  AND (approval_instance_id IS NULL OR approval_instance_id=?)
                """, instanceId, userId(), userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("COST_RULE_PLAN_STATE_INVALID", "成本规则方案状态已变化或尚未到生效日期");
    }

    void approveRulePlan(Long id, Long instanceId) {
        requireApprovedWorkflow(instanceId, WorkflowBusinessTypes.COST_RULE_PLAN, id);
        activate(id, instanceId, "SUBMITTED");
    }

    void rejectRulePlan(Long id, Long instanceId, String status) {
        String target = "WITHDRAWN".equals(status) ? "VALIDATED" : "REJECTED";
        int updated = jdbc.update("""
                UPDATE cost_subject_mapping_version SET status=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status='SUBMITTED' AND approval_instance_id=?
                """, target, userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("COST_RULE_PLAN_STATE_INVALID", "成本规则方案状态已变化");
    }

    private void activate(Long id, Long approvalInstanceId, String expectedStatus) {
        jdbc.queryForList("""
                SELECT id FROM cost_subject_mapping_version
                WHERE tenant_id=? ORDER BY id FOR UPDATE
                """, tenantId());
        Map<String, Object> version = one("""
                SELECT status,validation_report FROM cost_subject_mapping_version
                WHERE tenant_id=? AND id=? FOR UPDATE
                """, id);
        if (!expectedStatus.equals(String.valueOf(version.get("status")))) {
            throw new BusinessException("COST_SUBJECT_MAPPING_STATUS_INVALID", "成本规则方案状态已变化，无法启用");
        }
        Map<String, Object> currentReport = buildValidationReport(id, LocalDate.now());
        if (!Boolean.TRUE.equals(currentReport.get("passed"))) {
            throw new BusinessException("COST_RULE_PLAN_REVALIDATION_FAILED", "审批时方案已不再满足启用条件，旧方案保持生效");
        }
        if (!Objects.equals(String.valueOf(version.get("validation_report")), currentReport.toString())) {
            throw new BusinessException("COST_RULE_PLAN_CONTENT_DRIFT", "方案内容在校验后发生变化，请重新校验并提交");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cost_subject_mapping_item WHERE tenant_id=? AND mapping_version_id=?",
                Integer.class, tenantId(), id);
        if (count == null || count == 0) throw new BusinessException("COST_SUBJECT_MAPPING_EMPTY", "空映射版本不能启用");
        Integer effective = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_subject_mapping_version
                WHERE tenant_id=? AND id=? AND effective_date<=CURRENT_DATE
                """, Integer.class, tenantId(), id);
        if (effective == null || effective != 1) {
            throw new BusinessException("COST_RULE_PLAN_NOT_EFFECTIVE", "方案尚未到生效日期，不能启用");
        }
        Integer unmapped = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_subject_mapping_item i
                WHERE i.tenant_id=? AND i.mapping_version_id=? AND i.target_subject_id IS NOT NULL
                  AND EXISTS (SELECT 1 FROM cost_subject c WHERE c.tenant_id=i.tenant_id AND c.parent_id=i.target_subject_id AND c.deleted_flag=0)
                """, Integer.class, tenantId(), id);
        if (unmapped != null && unmapped > 0) throw new BusinessException("COST_SUBJECT_MAPPING_NON_LEAF", "映射目标必须为末级科目");
        jdbc.update("UPDATE cost_subject_mapping_version SET status='RETIRED',updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND status='ACTIVE' AND id<>?",
                userId(), tenantId(), id);
        int updated = jdbc.update("""
                UPDATE cost_subject_mapping_version SET status='ACTIVE',approval_instance_id=?,activated_by=?,
                    activated_at=CURRENT_TIMESTAMP,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status=?
                """, approvalInstanceId, userId(), userId(), tenantId(), id, expectedStatus);
        if (updated != 1) throw new BusinessException("COST_SUBJECT_MAPPING_STATUS_INVALID", "成本规则方案状态已变化，无法启用");
        jdbc.update("UPDATE cost_subject_assignment_rule SET status='RETIRED',updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND status='ACTIVE' AND mapping_version_id<>?",
                tenantId(), id);
        jdbc.update("UPDATE cost_subject_assignment_rule SET status='ACTIVE',updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND mapping_version_id=? AND status='DRAFT'",
                tenantId(), id);
    }

    List<Map<String, Object>> rules() {
        return jdbc.queryForList("""
                SELECT r.*,s.subject_code,s.subject_name,v.version_code,
                       p.project_code,p.project_name
                FROM cost_subject_assignment_rule r
                JOIN cost_subject s ON s.id=r.cost_subject_id AND s.tenant_id=r.tenant_id
                JOIN cost_subject_mapping_version v ON v.id=r.mapping_version_id AND v.tenant_id=r.tenant_id
                LEFT JOIN pm_project p ON p.id=r.project_id AND p.tenant_id=r.tenant_id
                WHERE r.tenant_id=? ORDER BY r.status,r.priority,r.rule_code
                """, tenantId());
    }

    List<Map<String, Object>> rules(Long versionId) {
        return jdbc.queryForList("""
                SELECT r.id,r.rule_code ruleCode,r.source_type sourceType,r.business_category businessCategory,
                       r.project_id projectId,r.cost_subject_id costSubjectId,r.priority,r.status,
                       r.effective_from effectiveFrom,r.effective_to effectiveTo,r.remark,
                       s.subject_code subjectCode,s.subject_name subjectName,p.project_code projectCode,p.project_name projectName
                FROM cost_subject_assignment_rule r
                JOIN cost_subject s ON s.tenant_id=r.tenant_id AND s.id=r.cost_subject_id
                LEFT JOIN pm_project p ON p.tenant_id=r.tenant_id AND p.id=r.project_id
                WHERE r.tenant_id=? AND r.mapping_version_id=? ORDER BY r.rule_code,r.id
                """, tenantId(), versionId);
    }

    Long createRule(RuleCommand command) {
        requireText(command.ruleCode(), "规则编码不能为空");
        requireText(command.sourceType(), "业务来源不能为空");
        requireSupportedSourceType(command.sourceType());
        requireSubject(command.costSubjectId(), true);
        requireMappingVersion(command.mappingVersionId(), "DRAFT");
        Integer ruleCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_subject_assignment_rule
                WHERE tenant_id=? AND mapping_version_id=?
                """, Integer.class, tenantId(), command.mappingVersionId());
        if (ruleCount != null && ruleCount >= APPROVAL_DETAIL_ROW_LIMIT) {
            throw new BusinessException("COST_RULE_PLAN_RULE_LIMIT_EXCEEDED", "单个成本规则方案最多维护1000条归集规则");
        }
        if (command.projectId() != null) requireProject(command.projectId());
        LocalDate from = command.effectiveFrom() == null ? LocalDate.now() : command.effectiveFrom();
        if (command.effectiveTo() != null && command.effectiveTo().isBefore(from)) {
            throw new BusinessException("COST_SUBJECT_RULE_DATE_INVALID", "规则失效日期不能早于生效日期");
        }
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO cost_subject_assignment_rule
                    (id,tenant_id,mapping_version_id,rule_code,source_type,business_category,project_id,cost_subject_id,
                     priority,status,effective_from,effective_to,created_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?)
                    """, id, tenantId(), command.mappingVersionId(), command.ruleCode().trim(), command.sourceType().trim(),
                    textOrDefault(command.businessCategory(), "*"), command.projectId(), command.costSubjectId(),
                    command.priority() == null ? 100 : command.priority(), from, command.effectiveTo(), userId(), command.remark());
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("COST_SUBJECT_RULE_DUPLICATE", "归集规则编码已存在", ex);
        }
        return id;
    }

    private void insertRule(Long mappingVersionId, MappingRule command) {
        requireText(command.ruleCode(), "规则编码不能为空");
        requireText(command.sourceType(), "业务来源不能为空");
        requireSupportedSourceType(command.sourceType());
        requireSubject(command.costSubjectId(), true);
        if (command.projectId() != null) requireProject(command.projectId());
        LocalDate from = command.effectiveFrom() == null ? LocalDate.now() : command.effectiveFrom();
        if (command.effectiveTo() != null && command.effectiveTo().isBefore(from)) {
            throw new BusinessException("COST_SUBJECT_RULE_DATE_INVALID", "规则失效日期不能早于生效日期");
        }
        jdbc.update("""
                INSERT INTO cost_subject_assignment_rule
                (id,tenant_id,mapping_version_id,rule_code,source_type,business_category,project_id,cost_subject_id,
                 priority,status,effective_from,effective_to,created_by,remark)
                VALUES (?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?)
                """, IdWorker.getId(), tenantId(), mappingVersionId, command.ruleCode().trim(), command.sourceType().trim(),
                textOrDefault(command.businessCategory(), "*"), command.projectId(), command.costSubjectId(),
                command.priority() == null ? 100 : command.priority(), from, command.effectiveTo(), userId(), command.remark());
    }

    private void requireSupportedSourceType(String sourceType) {
        String normalized = sourceType == null ? null : sourceType.trim();
        if (!REQUIRED_SOURCE_TYPES.contains(normalized)) {
            throw new BusinessException("COST_SUBJECT_RULE_SOURCE_UNSUPPORTED", "业务来源不属于权威成本确认节点");
        }
    }

    Long resolveRule(String sourceType, String businessCategory, Long projectId) {
        if (projectId != null) requireProject(projectId);
        requireText(sourceType, "业务来源不能为空");
        List<Map<String, Object>> result = jdbc.queryForList("""
                SELECT r.cost_subject_id,
                       CASE WHEN r.project_id IS NOT NULL THEN 0 ELSE 1 END project_rank,
                       CASE WHEN r.business_category=? THEN 0 ELSE 1 END category_rank,
                       r.priority
                FROM cost_subject_assignment_rule r
                JOIN cost_subject_mapping_version v ON v.id=r.mapping_version_id AND v.tenant_id=r.tenant_id AND v.status='ACTIVE'
                JOIN cost_subject s ON s.id=r.cost_subject_id AND s.tenant_id=r.tenant_id AND s.status='ENABLE' AND s.deleted_flag=0
                WHERE r.tenant_id=? AND r.status='ACTIVE' AND r.source_type=?
                  AND r.business_category IN (?, '*') AND (r.project_id=? OR r.project_id IS NULL)
                  AND r.effective_from<=CURRENT_DATE AND (r.effective_to IS NULL OR r.effective_to>=CURRENT_DATE)
                  AND NOT EXISTS (SELECT 1 FROM cost_subject c WHERE c.tenant_id=s.tenant_id AND c.parent_id=s.id AND c.deleted_flag=0)
                  AND NOT EXISTS (
                      SELECT 1 FROM project_cost_subject_scope p WHERE p.tenant_id=r.tenant_id AND p.project_id=?
                        AND p.cost_subject_id=r.cost_subject_id AND p.enabled=0 AND p.effective_from<=CURRENT_DATE
                        AND (p.effective_to IS NULL OR p.effective_to>=CURRENT_DATE))
                ORDER BY CASE WHEN r.project_id IS NOT NULL THEN 0 ELSE 1 END,
                         CASE WHEN r.business_category=? THEN 0 ELSE 1 END,r.priority,r.id
                LIMIT 2
                """, textOrDefault(businessCategory, "*"), tenantId(), sourceType.trim(),
                textOrDefault(businessCategory, "*"), projectId, projectId, textOrDefault(businessCategory, "*"));
        if (result.isEmpty()) throw new BusinessException("COST_SUBJECT_UNCLASSIFIED", "未命中启用的显式归集规则，单据保持待归类");
        if (result.size() > 1 && sameRuleRank(result.get(0), result.get(1))) {
            throw new BusinessException("COST_SUBJECT_RULE_AMBIGUOUS", "归集规则存在同等优先级冲突，请先消除歧义");
        }
        return longValue(result.get(0).get("cost_subject_id"));
    }

    List<Map<String, Object>> scopes(Long projectId) {
        requireProject(projectId);
        return jdbc.queryForList("""
                SELECT p.*,s.subject_code,s.subject_name FROM project_cost_subject_scope p
                JOIN cost_subject s ON s.id=p.cost_subject_id AND s.tenant_id=p.tenant_id
                WHERE p.tenant_id=? AND p.project_id=? ORDER BY s.subject_code
                """, tenantId(), projectId);
    }

    Long upsertScope(ScopeCommand command) {
        requireProject(command.projectId());
        requireSubject(command.costSubjectId(), true);
        LocalDate from = command.effectiveFrom() == null ? LocalDate.now() : command.effectiveFrom();
        if (command.effectiveTo() != null && command.effectiveTo().isBefore(from)) {
            throw new BusinessException("COST_SUBJECT_SCOPE_DATE_INVALID", "适用范围失效日期不能早于生效日期");
        }
        List<Long> existing = jdbc.query("SELECT id FROM project_cost_subject_scope WHERE tenant_id=? AND project_id=? AND cost_subject_id=?",
                (rs, rowNum) -> rs.getLong(1), tenantId(), command.projectId(), command.costSubjectId());
        if (!existing.isEmpty()) {
            jdbc.update("""
                    UPDATE project_cost_subject_scope SET enabled=?,effective_from=?,effective_to=?,version=version+1,
                        updated_by=?,updated_at=CURRENT_TIMESTAMP,remark=? WHERE tenant_id=? AND id=?
                    """, Boolean.FALSE.equals(command.enabled()) ? 0 : 1, from, command.effectiveTo(), userId(), command.remark(), tenantId(), existing.get(0));
            return existing.get(0);
        }
        Long id = IdWorker.getId();
        jdbc.update("""
                INSERT INTO project_cost_subject_scope
                (id,tenant_id,project_id,cost_subject_id,enabled,effective_from,effective_to,created_by,remark)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, id, tenantId(), command.projectId(), command.costSubjectId(),
                Boolean.FALSE.equals(command.enabled()) ? 0 : 1, from, command.effectiveTo(), userId(), command.remark());
        return id;
    }

    Map<String, Object> impact(Long subjectId) {
        requireSubject(subjectId, false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subjectId", subjectId);
        result.put("costItems", count("cost_item", "deleted_flag=0", subjectId));
        result.put("targetItems", count("cost_target_item", "deleted_flag=0", subjectId));
        result.put("forecastItems", count("cost_forecast_item", "1=1", subjectId));
        result.put("budgetLines", count("project_budget_line", "deleted_flag=0", subjectId));
        result.put("payments", count("pay_application", "deleted_flag=0", subjectId));
        result.put("expenses", count("expense_application", "deleted_flag=0", subjectId));
        result.put("settlementItems", count("stl_settlement_item", "deleted_flag=0", subjectId));
        result.put("accountingLines", count("accounting_entry_line", "1=1", subjectId));
        result.put("assignmentRules", count("cost_subject_assignment_rule", "1=1", subjectId));
        result.put("projectScopes", count("project_cost_subject_scope", "1=1", subjectId));
        return result;
    }


    long count(String table, String condition, Long subjectId) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND cost_subject_id=? AND " + condition,
                Long.class, tenantId(), subjectId);
        return value == null ? 0 : value;
    }

    static boolean sameRuleRank(Map<String, Object> first, Map<String, Object> second) {
        return intValue(first.get("project_rank")) == intValue(second.get("project_rank"))
                && intValue(first.get("category_rank")) == intValue(second.get("category_rank"))
                && intValue(first.get("priority")) == intValue(second.get("priority"));
    }

    private void addTemplateRule(List<MappingRule> rules, List<Map<String, Object>> subjects,
                                 String sourceType, String preferredType, String fallbackType, String suffix) {
        Map<String, Object> subject = subjects.stream()
                .filter(row -> preferredType.equals(String.valueOf(row.get("subject_type"))))
                .findFirst()
                .orElseGet(() -> subjects.stream()
                        .filter(row -> fallbackType.equals(String.valueOf(row.get("subject_type"))))
                        .findFirst().orElse(null));
        if (subject == null) return;
        rules.add(new MappingRule("RULE-" + sourceType + "-" + suffix, sourceType, "*", null,
                longValue(subject.get("id")), 100, LocalDate.now(), null, "标准初始规则，启用前须财务复核"));
    }

    private Set<String> planRows(Long versionId) {
        Set<String> rows = new HashSet<>();
        jdbc.queryForList("""
                SELECT source_subject_id,target_group_code,target_subject_id FROM cost_subject_mapping_item
                WHERE tenant_id=? AND mapping_version_id=?
                """, tenantId(), versionId).forEach(row -> rows.add("M|" + row.get("source_subject_id") + "|"
                + row.get("target_group_code") + "|" + row.get("target_subject_id")));
        jdbc.queryForList("""
                SELECT source_type,business_category,COALESCE(project_id,0) project_id,cost_subject_id,priority,effective_from,effective_to
                FROM cost_subject_assignment_rule WHERE tenant_id=? AND mapping_version_id=?
                """, tenantId(), versionId).forEach(row -> rows.add("R|" + row));
        return rows;
    }

    private void requireMappingVersionAnyStatus(Long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cost_subject_mapping_version WHERE tenant_id=? AND id=?",
                Integer.class, tenantId(), id);
        if (count == null || count != 1) throw new BusinessException("COST_SUBJECT_MAPPING_VERSION_INVALID", "成本规则方案不存在");
    }

}
