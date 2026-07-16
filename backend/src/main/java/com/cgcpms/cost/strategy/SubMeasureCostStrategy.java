package com.cgcpms.cost.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final CostSubjectResolver costSubjectResolver;

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

        BigDecimal netAmount = money(measure.getNetAmount());
        BigDecimal grossAmount = items.stream().map(SubMeasureItem::getAmount).map(SubMeasureCostStrategy::money)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (netAmount.compareTo(BigDecimal.ZERO) <= 0 || grossAmount.compareTo(BigDecimal.ZERO) <= 0
                || netAmount.compareTo(grossAmount) > 0) {
            throw new IllegalStateException("分包计量净额与明细金额不守恒，禁止生成成本 measureId=" + measureId);
        }

        LocalDate costDate = measure.getMeasureDate() == null ? LocalDate.now() : measure.getMeasureDate();

        // Resolve default cost subject for SUBCONTRACT type
        Long defaultSubjectId = costSubjectResolver.resolveDefaultSubjectId(measure.getTenantId(), "分包");

        int generated = 0;
        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (int index = 0; index < items.size(); index++) {
            SubMeasureItem item = items.get(index);
            BigDecimal allocatedAmount = index == items.size() - 1
                    ? netAmount.subtract(allocatedTotal)
                    : netAmount.multiply(money(item.getAmount())).divide(grossAmount, 2, RoundingMode.HALF_UP);
            allocatedTotal = allocatedTotal.add(allocatedAmount);
            CostItem cost = new CostItem();
            cost.setTenantId(measure.getTenantId());
            cost.setOrgId(null);
            cost.setProjectId(measure.getProjectId());
            cost.setContractId(measure.getContractId());
            cost.setPartnerId(measure.getPartnerId());
            cost.setCostType(COST_TYPE);
            cost.setCostSubjectId(defaultSubjectId);
            cost.setAmount(allocatedAmount);
            // Source item does not provide tax breakdown; assume full amount without tax
            cost.setTaxAmount(BigDecimal.ZERO);
            cost.setAmountWithoutTax(allocatedAmount);
            cost.setSourceType(SOURCE_TYPE);
            cost.setSourceId(measureId);
            cost.setSourceItemId(item.getId());
            cost.setCostDate(costDate);
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

    private static BigDecimal money(BigDecimal value) {
        return nvl(value).setScale(2, RoundingMode.HALF_UP);
    }

}
