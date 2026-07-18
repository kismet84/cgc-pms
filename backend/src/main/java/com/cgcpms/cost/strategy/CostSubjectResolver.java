package com.cgcpms.cost.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
}
