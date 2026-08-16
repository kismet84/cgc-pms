package com.cgcpms.cost.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.cgcpms.common.exception.BusinessException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Shared utility for resolving default cost subject IDs.
 * Extracted from 4 CostStrategy implementations to eliminate duplication.
 *
 * <p>Only an exact, enabled leaf {@code subject_type} match is returned. Missing mappings remain
 * unclassified instead of being silently assigned to a parent, root or unrelated enabled subject.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostSubjectResolver {

    private final CostSubjectMapper costSubjectMapper;
    private final JdbcTemplate jdbc;

    public record Decision(Long costSubjectId, Long mappingVersionId, Long assignmentRuleId,
                           Long originalCostSubjectId, Long overrideId, String classificationStatus,
                           Long snapshotId) {}

    /**
     * Resolve one immutable cost fact through the active rule plan. Finance overrides are evaluated
     * first, then project/category/priority ranking. Missing or tied matches fail closed.
     */
    public Decision resolveForFact(Long tenantId, Long projectId, String sourceType,
                                   String businessCategory, Long sourceId, Long sourceItemId,
                                   Long originalCostSubjectId, LocalDate asOfDate) {
        if (tenantId == null || projectId == null || sourceType == null || sourceType.isBlank()
                || sourceId == null || asOfDate == null) {
            throw new BusinessException("COST_CLASSIFICATION_CONTEXT_REQUIRED", "成本归类缺少租户、项目或业务来源");
        }
        long itemId = sourceItemId == null ? 0L : sourceItemId;
        List<Map<String, Object>> snapshots = jdbc.queryForList("""
                SELECT id,matched_cost_subject_id,mapping_version_id,assignment_rule_id,original_cost_subject_id,
                       classification_override_id,classification_status
                FROM cost_classification_snapshot
                WHERE tenant_id=? AND source_type=? AND source_id=? AND source_item_id=? AND status='PENDING'
                FOR UPDATE
                """, tenantId, sourceType.trim(), sourceId, itemId);
        if (snapshots.size() > 1) {
            throw new BusinessException("COST_CLASSIFICATION_SNAPSHOT_AMBIGUOUS", "同一成本来源存在多个归类快照");
        }
        if (!snapshots.isEmpty()) {
            Map<String, Object> row = snapshots.getFirst();
            Long subjectId = longValue(row.get("matched_cost_subject_id"));
            lockActualCostSubject(tenantId, subjectId);
            return new Decision(subjectId, longValue(row.get("mapping_version_id")),
                    longValue(row.get("assignment_rule_id")), longValue(row.get("original_cost_subject_id")),
                    longValue(row.get("classification_override_id")), String.valueOf(row.get("classification_status")),
                    longValue(row.get("id")));
        }
        List<Map<String, Object>> overrides = jdbc.queryForList("""
                SELECT o.id,o.override_cost_subject_id,o.mapping_version_id,o.assignment_rule_id
                FROM cost_classification_override o
                JOIN cost_subject s ON s.tenant_id=o.tenant_id AND s.id=o.override_cost_subject_id
                  AND s.deleted_flag=0 AND s.status='ENABLE' AND s.account_category='COST'
                WHERE o.tenant_id=? AND o.source_type=? AND o.source_id=? AND o.source_item_id=?
                  AND o.status='ACTIVE' AND NOT EXISTS (
                    SELECT 1 FROM cost_subject c WHERE c.tenant_id=s.tenant_id
                      AND c.parent_id=s.id AND c.deleted_flag=0)
                FOR UPDATE
                """, tenantId, sourceType.trim(), sourceId, itemId);
        if (overrides.size() > 1) {
            throw new BusinessException("COST_CLASSIFICATION_OVERRIDE_AMBIGUOUS", "同一成本来源存在多个有效财务覆盖");
        }
        if (!overrides.isEmpty()) {
            Map<String, Object> row = overrides.getFirst();
            Long subjectId = longValue(row.get("override_cost_subject_id"));
            lockActualCostSubject(tenantId, subjectId);
            requireProjectScope(tenantId, projectId, subjectId, asOfDate);
            return new Decision(subjectId, longValue(row.get("mapping_version_id")),
                    longValue(row.get("assignment_rule_id")), originalCostSubjectId,
                    longValue(row.get("id")), "OVERRIDDEN", null);
        }

        String category = businessCategory == null || businessCategory.isBlank()
                ? "*" : businessCategory.trim();
        List<Map<String, Object>> matches = jdbc.queryForList("""
                SELECT r.id,r.cost_subject_id,r.mapping_version_id,
                       CASE WHEN r.project_id IS NOT NULL THEN 0 ELSE 1 END project_rank,
                       CASE WHEN r.business_category=? THEN 0 ELSE 1 END category_rank,r.priority
                FROM cost_subject_assignment_rule r
                JOIN cost_subject_mapping_version v ON v.tenant_id=r.tenant_id
                  AND v.id=r.mapping_version_id AND v.status='ACTIVE'
                JOIN cost_subject s ON s.tenant_id=r.tenant_id AND s.id=r.cost_subject_id
                  AND s.deleted_flag=0 AND s.status='ENABLE' AND s.account_category='COST'
                WHERE r.tenant_id=? AND r.status='ACTIVE' AND r.source_type=?
                  AND r.business_category IN (?, '*') AND (r.project_id=? OR r.project_id IS NULL)
                  AND r.effective_from<=?
                  AND (r.effective_to IS NULL OR r.effective_to>=?)
                  AND NOT EXISTS (SELECT 1 FROM cost_subject c WHERE c.tenant_id=s.tenant_id
                                  AND c.parent_id=s.id AND c.deleted_flag=0)
                ORDER BY CASE WHEN r.project_id IS NOT NULL THEN 0 ELSE 1 END,
                         CASE WHEN r.business_category=? THEN 0 ELSE 1 END,r.priority,r.id
                LIMIT 2
                """, category, tenantId, sourceType.trim(), category, projectId,
                asOfDate, asOfDate, category);
        if (matches.isEmpty()) {
            throw new BusinessException("COST_SUBJECT_UNCLASSIFIED", "未命中启用的成本规则方案，业务保持待归类且不得提交或入账");
        }
        if (matches.size() > 1 && sameRank(matches.get(0), matches.get(1))) {
            throw new BusinessException("COST_SUBJECT_RULE_AMBIGUOUS", "成本规则方案存在同级冲突，请先修正规则");
        }
        Map<String, Object> row = matches.getFirst();
        Long subjectId = longValue(row.get("cost_subject_id"));
        lockActualCostSubject(tenantId, subjectId);
        requireProjectScope(tenantId, projectId, subjectId, asOfDate);
        return new Decision(subjectId, longValue(row.get("mapping_version_id")),
                longValue(row.get("id")), originalCostSubjectId, null, "CLASSIFIED", null);
    }

    public void markSnapshotPosted(Decision decision) {
        if (decision != null && decision.snapshotId() != null) {
            int updated = jdbc.update("""
                    UPDATE cost_classification_snapshot SET status='POSTED',posted_at=CURRENT_TIMESTAMP
                    WHERE id=? AND status='PENDING' AND matched_cost_subject_id=?
                      AND (mapping_version_id=? OR (mapping_version_id IS NULL AND ? IS NULL))
                      AND (assignment_rule_id=? OR (assignment_rule_id IS NULL AND ? IS NULL))
                      AND (classification_override_id=? OR (classification_override_id IS NULL AND ? IS NULL))
                    """, decision.snapshotId(), decision.costSubjectId(),
                    decision.mappingVersionId(), decision.mappingVersionId(),
                    decision.assignmentRuleId(), decision.assignmentRuleId(),
                    decision.overrideId(), decision.overrideId());
            if (updated != 1) {
                throw new BusinessException("COST_CLASSIFICATION_SNAPSHOT_STATE_INVALID", "成本归类快照状态已变化");
            }
        }
    }

    public boolean costFactExists(Long tenantId, String sourceType, Long sourceId,
                                  Long sourceItemId, String costType) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_item
                WHERE tenant_id=? AND source_type=? AND source_id=? AND source_item_id=?
                  AND cost_type=? AND deleted_flag=0
                """, Long.class, tenantId, sourceType, sourceId,
                sourceItemId == null ? 0L : sourceItemId, costType);
        return count != null && count > 0;
    }

    public Decision resolveForVersion(Long tenantId, Long projectId, String sourceType,
                                      String businessCategory, Long originalCostSubjectId,
                                      Long mappingVersionId, LocalDate asOfDate) {
        if (tenantId == null || projectId == null || mappingVersionId == null || asOfDate == null
                || sourceType == null || sourceType.isBlank()) {
            throw new BusinessException("COST_CLASSIFICATION_CONTEXT_REQUIRED", "历史试算缺少规则版本或业务上下文");
        }
        String category = businessCategory == null || businessCategory.isBlank() ? "*" : businessCategory.trim();
        List<Map<String, Object>> matches = jdbc.queryForList("""
                SELECT r.id,r.cost_subject_id,r.mapping_version_id,
                       CASE WHEN r.project_id IS NOT NULL THEN 0 ELSE 1 END project_rank,
                       CASE WHEN r.business_category=? THEN 0 ELSE 1 END category_rank,r.priority
                FROM cost_subject_assignment_rule r
                JOIN cost_subject_mapping_version v ON v.tenant_id=r.tenant_id AND v.id=r.mapping_version_id
                JOIN cost_subject s ON s.tenant_id=r.tenant_id AND s.id=r.cost_subject_id
                  AND s.deleted_flag=0 AND s.account_category='COST'
                WHERE r.tenant_id=? AND r.mapping_version_id=? AND r.source_type=?
                  AND r.business_category IN (?, '*') AND (r.project_id=? OR r.project_id IS NULL)
                  AND r.effective_from<=? AND (r.effective_to IS NULL OR r.effective_to>=?)
                  AND NOT EXISTS (SELECT 1 FROM cost_subject c WHERE c.tenant_id=s.tenant_id
                                  AND c.parent_id=s.id AND c.deleted_flag=0)
                ORDER BY CASE WHEN r.project_id IS NOT NULL THEN 0 ELSE 1 END,
                         CASE WHEN r.business_category=? THEN 0 ELSE 1 END,r.priority,r.id
                LIMIT 2
                """, category, tenantId, mappingVersionId, sourceType.trim(), category,
                projectId, asOfDate, asOfDate, category);
        if (matches.isEmpty()) {
            throw new BusinessException("COST_SUBJECT_UNCLASSIFIED", "指定规则方案未命中该历史成本事实");
        }
        if (matches.size() > 1 && sameRank(matches.get(0), matches.get(1))) {
            throw new BusinessException("COST_SUBJECT_RULE_AMBIGUOUS", "指定规则方案存在同级冲突");
        }
        Map<String, Object> row = matches.getFirst();
        Long subjectId = longValue(row.get("cost_subject_id"));
        Integer excluded = jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_cost_subject_scope_history h
                WHERE h.tenant_id=? AND h.project_id=? AND h.cost_subject_id=?
                  AND h.configuration_version=(
                    SELECT MAX(latest.configuration_version) FROM project_cost_subject_scope_history latest
                    WHERE latest.tenant_id=h.tenant_id AND latest.project_id=h.project_id
                      AND latest.cost_subject_id=h.cost_subject_id AND latest.effective_from<=?
                      AND (latest.effective_to IS NULL OR latest.effective_to>=?))
                  AND h.enabled=0
                """, Integer.class, tenantId, projectId, subjectId, asOfDate, asOfDate);
        if (excluded != null && excluded > 0) {
            throw new BusinessException("COST_SUBJECT_NOT_IN_PROJECT_SCOPE", "指定日期的项目配置已排除该成本科目");
        }
        return new Decision(subjectId, longValue(row.get("mapping_version_id")), longValue(row.get("id")),
                originalCostSubjectId, null, "CLASSIFIED", null);
    }

    /**
     * Resolve a default cost_subject_id for the given tenant by subject_type.
     * Exact leaf subject_type match only. Missing mappings return null for upstream handling.
     *
     * @param tenantId    the tenant ID
     * @param subjectType the subject type to match (e.g. "合同", "分包", "材料")
     * @return the resolved subject ID, or null if no subject exists
     */
    public Long resolveDefaultSubjectId(Long tenantId, String subjectType) {
        Long subjectId = findSubjectByType(tenantId, subjectType);
        if (subjectId == null) {
            log.warn("未配置 subject_type={} 的启用成本科目，保留待归类状态", subjectType);
        }
        return subjectId;
    }

    /**
     * Resolve a cost_subject_id for CT_CHANGE source type.
     * Exact "变更" subject_type match only.
     *
     * @param tenantId the tenant ID
     * @return the resolved subject ID, or null if no subject exists
     */
    public Long resolveForChange(Long tenantId) {
        return resolveDefaultSubjectId(tenantId, "变更");
    }

    /**
     * Find a subject by tenant and type. Returns null if not found (no fallback).
     */
    private Long findSubjectByType(Long tenantId, String subjectType) {
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, tenantId);
        wrapper.eq(CostSubject::getSubjectType, subjectType);
        wrapper.eq(CostSubject::getStatus, "ENABLE");
        wrapper.eq(CostSubject::getDeletedFlag, 0);
        wrapper.orderByDesc(CostSubject::getLevel, CostSubject::getSortOrder, CostSubject::getId);
        return costSubjectMapper.selectList(wrapper).stream()
                .filter(subject -> costSubjectMapper.selectCount(new LambdaQueryWrapper<CostSubject>()
                        .eq(CostSubject::getTenantId, tenantId)
                        .eq(CostSubject::getParentId, subject.getId())
                        .eq(CostSubject::getDeletedFlag, 0)) == 0)
                .map(CostSubject::getId)
                .findFirst()
                .orElse(null);
    }

    private void requireProjectScope(Long tenantId, Long projectId, Long subjectId, LocalDate asOfDate) {
        Integer excluded = jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_cost_subject_scope_history h
                WHERE h.tenant_id=? AND h.project_id=? AND h.cost_subject_id=? AND h.enabled=0
                  AND h.configuration_version=(
                    SELECT MAX(latest.configuration_version) FROM project_cost_subject_scope_history latest
                    WHERE latest.tenant_id=h.tenant_id AND latest.project_id=h.project_id
                      AND latest.cost_subject_id=h.cost_subject_id AND latest.effective_from<=?
                      AND (latest.effective_to IS NULL OR latest.effective_to>=?))
                """, Integer.class, tenantId, projectId, subjectId, asOfDate, asOfDate);
        if (excluded != null && excluded > 0) {
            throw new BusinessException("COST_SUBJECT_NOT_IN_PROJECT_SCOPE", "成本科目已被当前项目排除");
        }
    }

    private void lockActualCostSubject(Long tenantId, Long subjectId) {
        List<Long> subjects = jdbc.queryForList("""
                SELECT s.id FROM cost_subject s
                WHERE s.tenant_id=? AND s.id=? AND s.deleted_flag=0
                  AND s.status='ENABLE' AND s.account_category='COST'
                  AND NOT EXISTS (SELECT 1 FROM cost_subject child
                    WHERE child.tenant_id=s.tenant_id AND child.parent_id=s.id AND child.deleted_flag=0)
                FOR UPDATE
                """, Long.class, tenantId, subjectId);
        if (subjects.size() != 1) {
            throw new BusinessException("COST_SUBJECT_NOT_LEAF", "成本归集必须使用启用的成本域末级科目");
        }
        Integer disabledRules = jdbc.queryForObject("""
                SELECT COUNT(*) FROM overhead_allocation_rule
                WHERE tenant_id=? AND cost_subject_id=? AND status='DISABLE' AND deleted_flag=0
                """, Integer.class, tenantId, subjectId);
        if (disabledRules != null && disabledRules > 0) {
            throw new BusinessException("OVERHEAD_RULE_DISABLED_FOR_COST",
                    "该间接费科目的分摊规则已停用，重新启用后方可生成新成本事实");
        }
    }

    private static boolean sameRank(Map<String, Object> first, Map<String, Object> second) {
        return intValue(first.get("project_rank")) == intValue(second.get("project_rank"))
                && intValue(first.get("category_rank")) == intValue(second.get("category_rank"))
                && intValue(first.get("priority")) == intValue(second.get("priority"));
    }

    private static Long longValue(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
