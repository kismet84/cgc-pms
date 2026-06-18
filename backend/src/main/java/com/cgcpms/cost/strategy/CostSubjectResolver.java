package com.cgcpms.cost.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Shared utility for resolving default cost subject IDs.
 * Extracted from 4 CostStrategy implementations to eliminate duplication.
 *
 * <p>Resolution strategy (3-tier fallback):
 * <ol>
 *   <li>Match by subject_type (e.g. "合同", "分包", "材料")</li>
 *   <li>Fallback: any root-level subject (parent_id = 0)</li>
 *   <li>Last resort: any enabled subject</li>
 * </ol>
 *
 * <p>CT_CHANGE variant (4-tier fallback):
 * <ol>
 *   <li>Match "变更" subject_type</li>
 *   <li>Fallback: "合同" subject_type</li>
 *   <li>Fallback: root-level subject</li>
 *   <li>Last resort: any enabled subject</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostSubjectResolver {

    private final CostSubjectMapper costSubjectMapper;

    /**
     * Resolve a default cost_subject_id for the given tenant by subject_type.
     * 3-tier fallback: subject_type match → root-level → any enabled → null.
     *
     * @param tenantId    the tenant ID
     * @param subjectType the subject type to match (e.g. "合同", "分包", "材料")
     * @return the resolved subject ID, or null if no subject exists
     */
    public Long resolveDefaultSubjectId(Long tenantId, String subjectType) {
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, tenantId);
        wrapper.eq(CostSubject::getSubjectType, subjectType);
        wrapper.eq(CostSubject::getStatus, "ENABLE");
        wrapper.eq(CostSubject::getDeletedFlag, 0);
        wrapper.orderByAsc(CostSubject::getLevel);
        Page<CostSubject> page1 = new Page<>(0, 1);
        Page<CostSubject> result1 = costSubjectMapper.selectPage(page1, wrapper);
        CostSubject subject = result1.getRecords().isEmpty() ? null : result1.getRecords().get(0);
        if (subject != null) {
            return subject.getId();
        }

        // Fallback: root-level subject
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, tenantId);
        wrapper.eq(CostSubject::getParentId, 0L);
        wrapper.eq(CostSubject::getStatus, "ENABLE");
        wrapper.eq(CostSubject::getDeletedFlag, 0);
        wrapper.orderByAsc(CostSubject::getSortOrder);
        Page<CostSubject> page2 = new Page<>(0, 1);
        Page<CostSubject> result2 = costSubjectMapper.selectPage(page2, wrapper);
        subject = result2.getRecords().isEmpty() ? null : result2.getRecords().get(0);
        if (subject != null) {
            log.warn("未找到 subject_type={} 对应的科目，使用根科目 subjectId={}", subjectType, subject.getId());
            return subject.getId();
        }

        // Last resort: any enabled subject
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, tenantId);
        wrapper.eq(CostSubject::getStatus, "ENABLE");
        wrapper.eq(CostSubject::getDeletedFlag, 0);
        wrapper.orderByAsc(CostSubject::getLevel, CostSubject::getSortOrder);
        Page<CostSubject> page3 = new Page<>(0, 1);
        Page<CostSubject> result3 = costSubjectMapper.selectPage(page3, wrapper);
        subject = result3.getRecords().isEmpty() ? null : result3.getRecords().get(0);
        if (subject != null) {
            log.warn("未找到 subject_type={} 对应的科目且无根科目，使用第一个可用科目 subjectId={}", subjectType, subject.getId());
            return subject.getId();
        }

        log.error("租户 {} 下无任何启用科目，costSubjectId 将为 null，cost_type={}", tenantId, subjectType);
        return null;
    }

    /**
     * Resolve a cost_subject_id for CT_CHANGE source type.
     * 4-tier fallback: "变更" → "合同" → root-level → any enabled → null.
     *
     * @param tenantId the tenant ID
     * @return the resolved subject ID, or null if no subject exists
     */
    public Long resolveForChange(Long tenantId) {
        // 1. Try "变更" subject type
        Long id = findSubjectByType(tenantId, "变更");
        if (id != null) return id;

        // 2. Fallback: "合同" subject type (contract default)
        id = findSubjectByType(tenantId, "合同");
        if (id != null) {
            log.warn("未找到 subject_type=变更 对应的科目，使用合同科目 subjectId={}", id);
            return id;
        }

        // 3. Fallback: root-level subject
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, tenantId);
        wrapper.eq(CostSubject::getParentId, 0L);
        wrapper.eq(CostSubject::getStatus, "ENABLE");
        wrapper.eq(CostSubject::getDeletedFlag, 0);
        wrapper.orderByAsc(CostSubject::getSortOrder);
        Page<CostSubject> page4 = new Page<>(0, 1);
        Page<CostSubject> result4 = costSubjectMapper.selectPage(page4, wrapper);
        CostSubject subject = result4.getRecords().isEmpty() ? null : result4.getRecords().get(0);
        if (subject != null) {
            log.warn("未找到 subject_type=变更/合同 对应的科目，使用根科目 subjectId={}", subject.getId());
            return subject.getId();
        }

        // 4. Last resort: any enabled subject
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, tenantId);
        wrapper.eq(CostSubject::getStatus, "ENABLE");
        wrapper.eq(CostSubject::getDeletedFlag, 0);
        wrapper.orderByAsc(CostSubject::getLevel, CostSubject::getSortOrder);
        Page<CostSubject> page5 = new Page<>(0, 1);
        Page<CostSubject> result5 = costSubjectMapper.selectPage(page5, wrapper);
        subject = result5.getRecords().isEmpty() ? null : result5.getRecords().get(0);
        if (subject != null) {
            log.warn("未找到 subject_type=变更/合同 对应的科目且无根科目，使用第一个可用科目 subjectId={}", subject.getId());
            return subject.getId();
        }

        log.error("租户 {} 下无任何启用科目，costSubjectId 将为 null，source_type=CT_CHANGE", tenantId);
        return null;
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
        wrapper.orderByAsc(CostSubject::getLevel);
        Page<CostSubject> page6 = new Page<>(0, 1);
        Page<CostSubject> result6 = costSubjectMapper.selectPage(page6, wrapper);
        CostSubject subject = result6.getRecords().isEmpty() ? null : result6.getRecords().get(0);
        return subject != null ? subject.getId() : null;
    }
}
