package com.cgcpms.cost.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.cgcpms.common.util.BigDecimalUtils.nvl;

import java.time.LocalDate;
import java.util.List;

/**
 * Subcontractor measure cost generation strategy (SUB_MEASURE).
 * Generates subcontract cost records from approved measure items.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubMeasureCostStrategy implements CostGenerationStrategy {

    private static final String SOURCE_TYPE = "SUB_MEASURE";
    private static final String COST_TYPE = "SUBCONTRACT";
    private static final String COST_STATUS_CONFIRMED = "CONFIRMED";

    private final SubMeasureMapper subMeasureMapper;
    private final SubMeasureItemMapper subMeasureItemMapper;
    private final CostItemMapper costItemMapper;
    private final CostSubjectMapper costSubjectMapper;

    @Override
    public String supportSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateCost(Long sourceId) {
        Long measureId = sourceId;
        SubMeasure measure = subMeasureMapper.selectById(measureId);
        if (measure == null) {
            log.warn("生成分包成本：计量单不存在 measureId={}", measureId);
            return;
        }

        List<SubMeasureItem> items = subMeasureItemMapper.selectList(
                new LambdaQueryWrapper<SubMeasureItem>()
                        .eq(SubMeasureItem::getMeasureId, measureId));

        if (items.isEmpty()) {
            log.info("生成分包成本：计量单无明细，跳过 measureId={}", measureId);
            return;
        }

        LocalDate today = LocalDate.now();

        // Resolve default cost subject for SUBCONTRACT type
        Long defaultSubjectId = resolveDefaultSubjectId(measure.getTenantId(), "分包");

        int generated = 0;
        for (SubMeasureItem item : items) {
            CostItem cost = new CostItem();
            cost.setTenantId(measure.getTenantId());
            cost.setOrgId(null);
            cost.setProjectId(measure.getProjectId());
            cost.setContractId(measure.getContractId());
            cost.setPartnerId(measure.getPartnerId());
            cost.setCostType(COST_TYPE);
            cost.setCostSubjectId(defaultSubjectId);
            cost.setAmount(nvl(item.getAmount()));
            cost.setSourceType(SOURCE_TYPE);
            cost.setSourceId(measureId);
            cost.setSourceItemId(item.getId());
            cost.setCostDate(today);
            cost.setCostStatus(COST_STATUS_CONFIRMED);
            cost.setGeneratedFlag(1);

            try {
                costItemMapper.insert(cost);
                generated++;
            } catch (DuplicateKeyException e) {
                log.info("成本已存在，跳过 measureId={}, itemId={}", measureId, item.getId());
            }
        }

        // Update cost_generated_flag
        measure.setCostGeneratedFlag(1);
        subMeasureMapper.updateById(measure);

        log.info("生成分包成本完成 measureId={}, 明细数={}, 新增={}", measureId, items.size(), generated);
    }

    /**
     * Resolve a default cost_subject_id for the given tenant by subject_type.
     * Falls back to any root-level subject, then any enabled subject.
     */
    private Long resolveDefaultSubjectId(Long tenantId, String subjectType) {
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, tenantId);
        wrapper.eq(CostSubject::getSubjectType, subjectType);
        wrapper.eq(CostSubject::getStatus, "ENABLE");
        wrapper.eq(CostSubject::getDeletedFlag, 0);
        wrapper.orderByAsc(CostSubject::getLevel);
        wrapper.last("LIMIT 1");
        CostSubject subject = costSubjectMapper.selectOne(wrapper);
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
        wrapper.last("LIMIT 1");
        subject = costSubjectMapper.selectOne(wrapper);
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
        wrapper.last("LIMIT 1");
        subject = costSubjectMapper.selectOne(wrapper);
        if (subject != null) {
            log.warn("未找到 subject_type={} 对应的科目且无根科目，使用第一个可用科目 subjectId={}", subjectType, subject.getId());
            return subject.getId();
        }

        log.error("租户 {} 下无任何启用科目，costSubjectId 将为 null，cost_type={}", tenantId, subjectType);
        return null;
    }

}
