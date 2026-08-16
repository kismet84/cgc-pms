package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.strategy.CostSubjectResolver;
import com.cgcpms.project.auth.ProjectAccessChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Freezes the rule decision before a cost-bearing business document enters approval. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CostClassificationGuard {

    private final JdbcTemplate jdbc;
    private final CostSubjectResolver resolver;
    private final CostClassificationCaseRecorder caseRecorder;
    private final ProjectAccessChecker projectAccessChecker;

    private record SourceFact(Long tenantId, Long projectId, String sourceType, Long sourceId,
                              Long sourceItemId, String category, Long originalSubjectId,
                              LocalDate asOfDate) {}

    @Transactional(rollbackFor = Exception.class)
    public void requireClassified(String sourceType, Long sourceId) {
        if (sourceType == null || sourceId == null) {
            throw new BusinessException("COST_CLASSIFICATION_CONTEXT_REQUIRED", "成本归类业务来源不能为空");
        }
        List<SourceFact> facts = sourceFacts(sourceType, sourceId);
        for (SourceFact fact : facts) {
            try {
                lockOpenCase(fact);
                CostSubjectResolver.Decision decision = resolver.resolveForFact(
                        fact.tenantId(), fact.projectId(), fact.sourceType(), fact.category(),
                        fact.sourceId(), fact.sourceItemId(), fact.originalSubjectId(), fact.asOfDate());
                if (decision.snapshotId() == null) insertSnapshot(fact, decision);
                resolveOpenCase(fact);
            } catch (BusinessException exception) {
                if (isRecoverableClassificationFailure(exception.getCode())) {
                    recordCaseAfterRollback(fact, exception);
                }
                throw exception;
            }
        }
    }

    Long overrideClassification(Long caseId, Long snapshotId, Long targetSubjectId, String reason) {
        if ((caseId == null) == (snapshotId == null)) {
            throw new BusinessException("COST_CLASSIFICATION_OVERRIDE_TARGET_INVALID", "待归类记录与冻结快照必须且只能选择一个");
        }
        if (targetSubjectId == null || reason == null || reason.isBlank()) {
            throw new BusinessException("COST_CLASSIFICATION_OVERRIDE_REQUIRED", "覆盖科目和原因不能为空");
        }
        Map<String, Object> source;
        if (caseId != null) {
            source = classificationCaseForUpdate(caseId);
        } else {
            Map<String, Object> snapshot = snapshotDetails(snapshotId);
            lockClassificationCaseForSource(String.valueOf(snapshot.get("source_type")),
                    longValue(snapshot.get("source_id")), longValue(snapshot.get("source_item_id")));
            source = snapshotForUpdate(snapshotId);
        }
        Long projectId = longValue(source.get("project_id"));
        projectAccessChecker.checkAccess(projectId, "覆盖成本归类");
        requireTargetSubject(projectId, targetSubjectId);
        String sourceType = String.valueOf(source.get("source_type"));
        Long sourceId = longValue(source.get("source_id"));
        Long sourceItemId = longValue(source.get("source_item_id"));
        long normalizedItemId = sourceItemId == null ? 0L : sourceItemId;
        Long effectiveSnapshotId = snapshotId;
        if (caseId != null) {
            Map<String, Object> pendingSnapshot = pendingSnapshotForSourceForUpdate(
                    sourceType, sourceId, normalizedItemId);
            if (pendingSnapshot != null) {
                effectiveSnapshotId = longValue(pendingSnapshot.get("id"));
                source = pendingSnapshot;
            }
        }
        Map<String, Object> activeOverride = activeOverrideForUpdate(sourceType, sourceId, normalizedItemId);
        Integer facts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_item
                WHERE tenant_id=? AND source_type=? AND source_id=? AND source_item_id=? AND deleted_flag=0
                """, Integer.class, tenantId(), sourceType, sourceId, normalizedItemId);
        if (facts != null && facts > 0) {
            throw new BusinessException("COST_CLASSIFICATION_ALREADY_POSTED", "成本事实已生成，不能覆盖历史归类");
        }
        if (activeOverride != null) {
            Long activeId = longValue(activeOverride.get("id"));
            Long activeTarget = longValue(activeOverride.get("override_cost_subject_id"));
            String activeReason = String.valueOf(activeOverride.get("override_reason"));
            if (targetSubjectId.equals(activeTarget) && reason.trim().equals(activeReason)) {
                applyOverrideToPendingSnapshot(effectiveSnapshotId, targetSubjectId, activeId);
                resolveCase(caseId);
                return activeId;
            }
            source = activeOverride;
            int retired = jdbc.update("""
                    UPDATE cost_classification_override
                    SET status='RETIRED',retired_by=?,retired_at=CURRENT_TIMESTAMP,version=version+1
                    WHERE tenant_id=? AND id=? AND status='ACTIVE'
                    """, userId(), tenantId(), activeId);
            if (retired != 1) {
                throw new BusinessException("COST_CLASSIFICATION_OVERRIDE_CONFLICT", "财务覆盖已变化，请刷新后重试");
            }
        }
        Long overrideId = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO cost_classification_override
                    (id,tenant_id,source_type,source_id,source_item_id,original_cost_subject_id,
                     matched_cost_subject_id,override_cost_subject_id,mapping_version_id,assignment_rule_id,
                     override_reason,status,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?)
                    """, overrideId, tenantId(), sourceType, sourceId, normalizedItemId,
                    source.get("original_cost_subject_id"), source.get("matched_cost_subject_id"), targetSubjectId,
                    source.get("mapping_version_id"), source.get("assignment_rule_id"), reason.trim(), userId());
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException("COST_CLASSIFICATION_OVERRIDE_CONFLICT", "该来源已有有效财务覆盖", duplicate);
        }
        applyOverrideToPendingSnapshot(effectiveSnapshotId, targetSubjectId, overrideId);
        resolveCase(caseId);
        return overrideId;
    }

    private void applyOverrideToPendingSnapshot(Long snapshotId, Long targetSubjectId, Long overrideId) {
        if (snapshotId == null) return;
        int updated = jdbc.update("""
                UPDATE cost_classification_snapshot
                SET matched_cost_subject_id=?,classification_override_id=?,classification_status='OVERRIDDEN'
                WHERE tenant_id=? AND id=? AND status='PENDING'
                """, targetSubjectId, overrideId, tenantId(), snapshotId);
        if (updated != 1) {
            throw new BusinessException("COST_CLASSIFICATION_SNAPSHOT_STATE_INVALID", "成本归类快照状态已变化");
        }
    }

    private void resolveCase(Long caseId) {
        if (caseId == null) return;
        String status = jdbc.queryForObject("""
                SELECT status FROM cost_unclassified_case WHERE tenant_id=? AND id=?
                """, String.class, tenantId(), caseId);
        if ("RESOLVED".equals(status)) return;
        int updated = jdbc.update("""
                UPDATE cost_unclassified_case SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP,
                    updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND status='OPEN'
                """, tenantId(), caseId);
        if (updated != 1) {
            throw new BusinessException("COST_CLASSIFICATION_CASE_STATE_INVALID", "待归类记录状态已变化");
        }
    }

    private void lockOpenCase(SourceFact fact) {
        jdbc.queryForList("""
                SELECT id FROM cost_unclassified_case
                WHERE tenant_id=? AND source_type=? AND source_id=? AND source_item_id=?
                FOR UPDATE
                """, fact.tenantId(), fact.sourceType(), fact.sourceId(),
                fact.sourceItemId() == null ? 0L : fact.sourceItemId());
    }

    private void lockClassificationCaseForSource(String sourceType, Long sourceId, Long sourceItemId) {
        jdbc.queryForList("""
                SELECT id FROM cost_unclassified_case
                WHERE tenant_id=? AND source_type=? AND source_id=? AND source_item_id=?
                FOR UPDATE
                """, tenantId(), sourceType, sourceId, sourceItemId == null ? 0L : sourceItemId);
    }

    private void recordCaseAfterRollback(SourceFact fact, BusinessException exception) {
        Runnable record = () -> {
            try {
                caseRecorder.record(fact.tenantId(), fact.projectId(), fact.sourceType(), fact.sourceId(),
                        fact.sourceItemId(), fact.category(), fact.originalSubjectId(),
                        exception.getCode(), exception.getMessage());
            } catch (RuntimeException recordingFailure) {
                log.error("成本待归类留痕失败 sourceType={} sourceId={} sourceItemId={}",
                        fact.sourceType(), fact.sourceId(), fact.sourceItemId(), recordingFailure);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            record.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) record.run();
            }
        });
    }

    private Map<String, Object> pendingSnapshotForSourceForUpdate(String sourceType, Long sourceId, Long sourceItemId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,project_id,source_type,source_id,source_item_id,original_cost_subject_id,
                       matched_cost_subject_id,mapping_version_id,assignment_rule_id
                FROM cost_classification_snapshot
                WHERE tenant_id=? AND source_type=? AND source_id=? AND source_item_id=? AND status='PENDING'
                FOR UPDATE
                """, tenantId(), sourceType, sourceId, sourceItemId);
        if (rows.size() > 1) {
            throw new BusinessException("COST_CLASSIFICATION_SNAPSHOT_AMBIGUOUS", "同一成本来源存在多个归类快照");
        }
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidPending(String sourceType, Long sourceId) {
        jdbc.update("""
                UPDATE cost_classification_snapshot SET status='VOID'
                WHERE tenant_id=? AND source_type=? AND source_id=? AND status='PENDING'
                """, tenantId(), sourceType, sourceId);
    }

    private void insertSnapshot(SourceFact fact, CostSubjectResolver.Decision decision) {
        try {
            jdbc.update("""
                    INSERT INTO cost_classification_snapshot
                    (id,tenant_id,source_type,source_id,source_item_id,project_id,original_cost_subject_id,
                     matched_cost_subject_id,mapping_version_id,assignment_rule_id,classification_override_id,
                     classification_status,business_category,status,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?)
                    """, IdWorker.getId(), fact.tenantId(), fact.sourceType(), fact.sourceId(), fact.sourceItemId(),
                    fact.projectId(), fact.originalSubjectId(), decision.costSubjectId(), decision.mappingVersionId(),
                    decision.assignmentRuleId(), decision.overrideId(), decision.classificationStatus(), fact.category(), userId());
        } catch (DuplicateKeyException duplicate) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM cost_classification_snapshot
                    WHERE tenant_id=? AND source_type=? AND source_id=? AND source_item_id=? AND status='PENDING'
                    """, Integer.class, fact.tenantId(), fact.sourceType(), fact.sourceId(), fact.sourceItemId());
            if (count == null || count != 1) {
                throw new BusinessException("COST_CLASSIFICATION_SNAPSHOT_CONFLICT", "成本归类快照已变化，请刷新后重试", duplicate);
            }
        }
    }

    private Map<String, Object> classificationCaseForUpdate(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,project_id,source_type,source_id,source_item_id,original_cost_subject_id,
                       NULL matched_cost_subject_id,NULL mapping_version_id,NULL assignment_rule_id,status
                FROM cost_unclassified_case
                WHERE tenant_id=? AND id=? FOR UPDATE
                """, tenantId(), id);
        if (rows.size() != 1) throw new BusinessException("COST_CLASSIFICATION_CASE_NOT_FOUND", "待归类记录不存在");
        return rows.getFirst();
    }

    private Map<String, Object> activeOverrideForUpdate(String sourceType, Long sourceId, Long sourceItemId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,original_cost_subject_id,matched_cost_subject_id,override_cost_subject_id,
                       mapping_version_id,assignment_rule_id,override_reason
                FROM cost_classification_override
                WHERE tenant_id=? AND source_type=? AND source_id=? AND source_item_id=? AND status='ACTIVE'
                FOR UPDATE
                """, tenantId(), sourceType, sourceId, sourceItemId);
        if (rows.size() > 1) {
            throw new BusinessException("COST_CLASSIFICATION_OVERRIDE_AMBIGUOUS", "同一成本来源存在多个有效财务覆盖");
        }
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Map<String, Object> snapshotForUpdate(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,project_id,source_type,source_id,source_item_id,original_cost_subject_id,
                       matched_cost_subject_id,mapping_version_id,assignment_rule_id
                FROM cost_classification_snapshot
                WHERE tenant_id=? AND id=? AND status='PENDING' FOR UPDATE
                """, tenantId(), id);
        if (rows.size() != 1) throw new BusinessException("COST_CLASSIFICATION_SNAPSHOT_NOT_FOUND", "冻结归类快照不存在或已处理");
        return rows.getFirst();
    }

    private Map<String, Object> snapshotDetails(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,source_type,source_id,source_item_id
                FROM cost_classification_snapshot
                WHERE tenant_id=? AND id=? AND status='PENDING'
                """, tenantId(), id);
        if (rows.size() != 1) {
            throw new BusinessException("COST_CLASSIFICATION_SNAPSHOT_NOT_FOUND", "冻结归类快照不存在或已处理");
        }
        return rows.getFirst();
    }

    private void requireTargetSubject(Long projectId, Long subjectId) {
        List<Map<String, Object>> subjects = jdbc.queryForList("""
                SELECT id,status,account_category
                FROM cost_subject
                WHERE tenant_id=? AND id=? AND deleted_flag=0
                FOR UPDATE
                """, tenantId(), subjectId);
        if (subjects.size() != 1
                || !"ENABLE".equals(String.valueOf(subjects.getFirst().get("status")))
                || !"COST".equals(String.valueOf(subjects.getFirst().get("account_category")))) {
            throw new BusinessException("COST_SUBJECT_NOT_ENABLED_LEAF", "覆盖目标必须是启用的末级成本科目");
        }
        Integer children = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_subject
                WHERE tenant_id=? AND parent_id=? AND deleted_flag=0
                """, Integer.class, tenantId(), subjectId);
        if (children == null || children != 0) {
            throw new BusinessException("COST_SUBJECT_NOT_ENABLED_LEAF", "覆盖目标必须是启用的末级成本科目");
        }
        Integer disabledOverheadRules = jdbc.queryForObject("""
                SELECT COUNT(*) FROM overhead_allocation_rule
                WHERE tenant_id=? AND cost_subject_id=? AND status='DISABLE' AND deleted_flag=0
                """, Integer.class, tenantId(), subjectId);
        if (disabledOverheadRules != null && disabledOverheadRules > 0) {
            throw new BusinessException("OVERHEAD_RULE_DISABLED_FOR_COST",
                    "该间接费科目的分摊规则已停用，不能作为财务覆盖目标");
        }
        Integer excluded = jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_cost_subject_scope_history h
                WHERE h.tenant_id=? AND h.project_id=? AND h.cost_subject_id=? AND h.enabled=0
                  AND h.configuration_version=(SELECT MAX(latest.configuration_version)
                    FROM project_cost_subject_scope_history latest
                    WHERE latest.tenant_id=h.tenant_id AND latest.project_id=h.project_id
                      AND latest.cost_subject_id=h.cost_subject_id AND latest.effective_from<=CURRENT_DATE
                      AND (latest.effective_to IS NULL OR latest.effective_to>=CURRENT_DATE))
                """, Integer.class, tenantId(), projectId, subjectId);
        if (excluded != null && excluded > 0) {
            throw new BusinessException("COST_SUBJECT_NOT_IN_PROJECT_SCOPE", "覆盖目标已被当前项目排除");
        }
    }

    private void resolveOpenCase(SourceFact fact) {
        jdbc.update("""
                UPDATE cost_unclassified_case SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP,
                    updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND source_type=? AND source_id=? AND source_item_id=? AND status='OPEN'
                """, fact.tenantId(), fact.sourceType(), fact.sourceId(), fact.sourceItemId());
    }

    private static boolean isRecoverableClassificationFailure(String code) {
        return List.of("COST_SUBJECT_UNCLASSIFIED", "COST_SUBJECT_RULE_AMBIGUOUS",
                "COST_SUBJECT_NOT_IN_PROJECT_SCOPE").contains(code);
    }

    private List<SourceFact> sourceFacts(String sourceType, Long sourceId) {
        return switch (sourceType) {
            case "CT_CONTRACT" -> contractFacts(sourceId);
            case "MAT_RECEIPT" -> receiptFacts(sourceId);
            case "MAT_REQUISITION" -> detailFacts(sourceType, sourceId,
                    "mat_requisition", "mat_requisition_item", "requisition_id", "requisition_date", null);
            case "SUB_MEASURE" -> detailFacts(sourceType, sourceId,
                    "sub_measure", "sub_measure_item", "measure_id", "measure_date", null);
            case "VAR_ORDER" -> variationFacts(sourceId);
            case "CT_CHANGE" -> contractChangeFacts(sourceId);
            case "QUALITY_SAFETY_CONSEQUENCE" -> consequenceFacts(sourceId);
            default -> throw new BusinessException("COST_CLASSIFICATION_SOURCE_UNSUPPORTED", "未登记的成本权威来源: " + sourceType);
        };
    }

    private List<SourceFact> contractFacts(Long sourceId) {
        Map<String, Object> header = one("""
                SELECT tenant_id,project_id,contract_type,CURRENT_DATE business_date FROM ct_contract
                WHERE tenant_id=? AND id=? AND deleted_flag=0
                """, sourceId);
        if ("MAIN".equals(header.get("contract_type"))) return List.of();
        return rows("""
                SELECT id source_item_id,NULL original_subject_id FROM ct_contract_item
                WHERE tenant_id=? AND contract_id=? AND deleted_flag=0 AND amount<>0
                """, sourceId).stream().map(row -> fact(header, "CT_CONTRACT", sourceId, row,
                        String.valueOf(header.get("contract_type")))).toList();
    }

    private List<SourceFact> receiptFacts(Long sourceId) {
        Map<String, Object> header = one("""
                SELECT tenant_id,project_id,receipt_mode,receipt_date business_date FROM mat_receipt
                WHERE tenant_id=? AND id=? AND deleted_flag=0
                """, sourceId);
        if (!"DIRECT_CONSUMPTION".equals(header.get("receipt_mode"))) return List.of();
        return rows("""
                SELECT id source_item_id,NULL original_subject_id FROM mat_receipt_item
                WHERE tenant_id=? AND receipt_id=? AND amount>0
                """, sourceId).stream().map(row -> fact(header, "MAT_RECEIPT", sourceId, row,
                        String.valueOf(header.get("receipt_mode")))).toList();
    }

    private List<SourceFact> detailFacts(String sourceType, Long sourceId, String headerTable,
                                         String lineTable, String lineForeignKey, String dateColumn,
                                         String category) {
        Map<String, Object> header = one("SELECT tenant_id,project_id," + dateColumn + " business_date FROM " + headerTable
                + " WHERE tenant_id=? AND id=? AND deleted_flag=0", sourceId);
        List<Map<String, Object>> lines = rows("SELECT id source_item_id,NULL original_subject_id FROM "
                + lineTable + " WHERE tenant_id=? AND " + lineForeignKey + "=? AND deleted_flag=0", sourceId);
        return lines.stream().map(row -> fact(header, sourceType, sourceId, row, category)).toList();
    }

    private List<SourceFact> variationFacts(Long sourceId) {
        Map<String, Object> header = one("""
                SELECT tenant_id,project_id,direction,estimated_cost_amount FROM var_order
                WHERE tenant_id=? AND id=? AND deleted_flag=0
                """, sourceId);
        boolean costBearing = "COST".equals(header.get("direction"))
                || money(header.get("estimated_cost_amount")).signum() > 0;
        if (!costBearing) return List.of();
        return rows("""
                SELECT id source_item_id,cost_subject_id original_subject_id FROM var_order_item
                WHERE tenant_id=? AND var_order_id=? AND deleted_flag=0 AND amount<>0
                """, sourceId).stream().map(row -> fact(header, "VAR_ORDER", sourceId, row, "*")).toList();
    }

    private List<SourceFact> contractChangeFacts(Long sourceId) {
        Map<String, Object> header = one("""
                SELECT c.tenant_id,c.project_id,c.source_var_order_id,ct.contract_type FROM ct_contract_change c
                JOIN ct_contract ct ON ct.tenant_id=c.tenant_id AND ct.id=c.contract_id AND ct.deleted_flag=0
                WHERE c.tenant_id=? AND c.id=? AND c.deleted_flag=0
                """, sourceId);
        if ("MAIN".equals(header.get("contract_type")) || header.get("source_var_order_id") != null) return List.of();
        return List.of(new SourceFact(longValue(header.get("tenant_id")), longValue(header.get("project_id")),
                "CT_CHANGE", sourceId, 0L, "*", null, LocalDate.now()));
    }

    private List<SourceFact> consequenceFacts(Long sourceId) {
        Map<String, Object> header = one("""
                SELECT c.tenant_id,c.project_id,c.cost_subject_id original_subject_id,i.issue_type
                FROM qs_consequence c JOIN qs_issue i ON i.tenant_id=c.tenant_id AND i.id=c.issue_id
                WHERE c.tenant_id=? AND c.id=? AND c.deleted_flag=0
                """, sourceId);
        return List.of(new SourceFact(longValue(header.get("tenant_id")), longValue(header.get("project_id")),
                "QUALITY_SAFETY_CONSEQUENCE", sourceId, 0L, text(header.get("issue_type")),
                longValue(header.get("original_subject_id")), LocalDate.now()));
    }

    private SourceFact fact(Map<String, Object> header, String sourceType, Long sourceId,
                            Map<String, Object> row, String category) {
        return new SourceFact(longValue(header.get("tenant_id")), longValue(header.get("project_id")), sourceType,
                sourceId, longValue(row.get("source_item_id")), category == null ? "*" : category,
                longValue(row.get("original_subject_id")), localDate(header.get("business_date")));
    }

    private Map<String, Object> one(String sql, Long id) {
        List<Map<String, Object>> result = jdbc.queryForList(sql, tenantId(), id);
        if (result.size() != 1) throw new BusinessException("BUSINESS_SOURCE_NOT_FOUND", "成本业务来源不存在或不可用");
        return result.getFirst();
    }

    private List<Map<String, Object>> rows(String sql, Long id) {
        return new ArrayList<>(jdbc.queryForList(sql, tenantId(), id));
    }

    private Long tenantId() {
        Long value = UserContext.getCurrentTenantId();
        if (value == null) throw new BusinessException("TENANT_CONTEXT_REQUIRED", "租户上下文缺失");
        return value;
    }

    private Long userId() {
        Long value = UserContext.getCurrentUserId();
        if (value == null) throw new BusinessException("USER_CONTEXT_REQUIRED", "用户上下文缺失");
        return value;
    }

    private static Long longValue(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? "*" : String.valueOf(value);
    }

    private static LocalDate localDate(Object value) {
        if (value == null) return LocalDate.now();
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }

    private static java.math.BigDecimal money(Object value) {
        return value == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(String.valueOf(value));
    }
}
