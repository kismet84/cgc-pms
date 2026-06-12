package com.cgcpms.cost.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.cgcpms.common.util.BigDecimalUtils.nvl;

import java.time.LocalDate;
import java.util.List;

/**
 * Material receipt cost generation strategy (MAT_RECEIPT).
 * Generates material cost records from approved material receipt items.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialReceiptCostStrategy implements CostGenerationStrategy {

    private static final String SOURCE_TYPE = "MAT_RECEIPT";
    private static final String COST_TYPE = "MATERIAL";
    private static final String COST_STATUS_CONFIRMED = "CONFIRMED";

    private final MatReceiptMapper matReceiptMapper;
    private final MatReceiptItemMapper matReceiptItemMapper;
    private final CostItemMapper costItemMapper;
    private final CostSubjectMapper costSubjectMapper;

    @Override
    public String supportSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateCost(Long sourceId) {
        Long receiptId = sourceId;
        MatReceipt receipt = matReceiptMapper.selectById(receiptId);
        if (receipt == null) {
            log.warn("生成材料成本：验收单不存在 receiptId={}", receiptId);
            return;
        }

        List<MatReceiptItem> items = matReceiptItemMapper.selectList(
                new LambdaQueryWrapper<MatReceiptItem>()
                        .eq(MatReceiptItem::getReceiptId, receiptId));

        if (items.isEmpty()) {
            log.info("生成材料成本：验收单无明细，跳过 receiptId={}", receiptId);
            return;
        }

        LocalDate today = LocalDate.now();

        // Resolve default cost subject for MATERIAL type
        Long defaultSubjectId = resolveDefaultSubjectId(receipt.getTenantId(), "材料");

        int generated = 0;
        for (MatReceiptItem item : items) {
            CostItem cost = new CostItem();
            cost.setTenantId(receipt.getTenantId());
            cost.setOrgId(null);
            cost.setProjectId(receipt.getProjectId());
            cost.setContractId(receipt.getContractId());
            cost.setPartnerId(receipt.getPartnerId());
            cost.setCostType(COST_TYPE);
            cost.setCostSubjectId(defaultSubjectId);
            cost.setAmount(nvl(item.getAmount()));
            cost.setSourceType(SOURCE_TYPE);
            cost.setSourceId(receiptId);
            cost.setSourceItemId(item.getId());
            cost.setCostDate(today);
            cost.setCostStatus(COST_STATUS_CONFIRMED);
            cost.setGeneratedFlag(1);

            try {
                costItemMapper.insert(cost);
                generated++;
            } catch (DuplicateKeyException e) {
                log.info("成本已存在，跳过 receiptId={}, itemId={}", receiptId, item.getId());
            }
        }

        // Update cost_generated_flag
        receipt.setCostGeneratedFlag(1);
        matReceiptMapper.updateById(receipt);

        log.info("生成材料成本完成 receiptId={}, 明细数={}, 新增={}", receiptId, items.size(), generated);
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
