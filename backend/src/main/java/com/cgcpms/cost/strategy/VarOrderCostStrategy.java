package com.cgcpms.cost.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.entity.VarOrderItem;
import com.cgcpms.variation.mapper.VarOrderItemMapper;
import com.cgcpms.variation.mapper.VarOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.cgcpms.common.util.BigDecimalUtils.nvl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Variation order cost generation strategy (VAR_ORDER).
 * Generates variation cost records from approved variation order items.
 * Only processes orders with direction = 'COST' (per W0 decision 4).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VarOrderCostStrategy implements CostGenerationStrategy {

    private static final String SOURCE_TYPE = "VAR_ORDER";
    private static final String COST_TYPE = "VARIATION";
    private static final String COST_STATUS_CONFIRMED = "CONFIRMED";

    private final VarOrderMapper varOrderMapper;
    private final VarOrderItemMapper varOrderItemMapper;
    private final CostItemMapper costItemMapper;
    private final CostSubjectResolver costSubjectResolver;
    private final AccountingPeriodGuard accountingPeriodGuard;

    @Override
    public String supportSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateCost(Long sourceId) {
        Long varOrderId = sourceId;
        VarOrder varOrder = varOrderMapper.selectById(varOrderId);
        if (varOrder == null) {
            log.warn("生成变更成本：变更单不存在 varOrderId={}", varOrderId);
            return;
        }

        List<VarOrderItem> items = varOrderItemMapper.selectList(
                new LambdaQueryWrapper<VarOrderItem>()
                        .eq(VarOrderItem::getVarOrderId, varOrderId));

        if (items.isEmpty()) {
            log.info("生成变更成本：变更单无明细，跳过 varOrderId={}", varOrderId);
            return;
        }

        LocalDate today = LocalDate.now();
        accountingPeriodGuard.assertWritable(today);
        int generated = 0;
        for (VarOrderItem item : items) {
            if (costSubjectResolver.costFactExists(varOrder.getTenantId(), SOURCE_TYPE,
                    varOrderId, item.getId(), COST_TYPE)) {
                continue;
            }
            CostItem cost = new CostItem();
            cost.setTenantId(varOrder.getTenantId());
            cost.setOrgId(null);
            cost.setProjectId(varOrder.getProjectId());
            cost.setWbsTaskId(item.getWbsTaskId());
            cost.setContractId(varOrder.getContractId());
            cost.setPartnerId(varOrder.getPartnerId());
            cost.setCostType(COST_TYPE);
            CostSubjectResolver.Decision decision = costSubjectResolver.resolveForFact(
                    varOrder.getTenantId(), varOrder.getProjectId(), SOURCE_TYPE,
                    "*", varOrderId, item.getId(), item.getCostSubjectId(), today);
            applyDecision(cost, decision);
            cost.setClassificationBusinessCategory("*");
            cost.setAmount(nvl(item.getAmount()));
            // Source item does not provide tax breakdown; assume full amount without tax
            cost.setTaxAmount(BigDecimal.ZERO);
            cost.setAmountWithoutTax(nvl(item.getAmount()));
            cost.setSourceType(SOURCE_TYPE);
            cost.setSourceId(varOrderId);
            cost.setSourceItemId(item.getId());
            cost.setCostDate(today);
            cost.setCostStatus(COST_STATUS_CONFIRMED);
            cost.setGeneratedFlag(1);

            try {
                costItemMapper.insert(cost);
                costSubjectResolver.markSnapshotPosted(decision);
                generated++;
            } catch (DuplicateKeyException e) {
                costSubjectResolver.markSnapshotPosted(decision);
                log.info("成本已存在，跳过 varOrderId={}, itemId={}", varOrderId, item.getId());
            }
        }

        // Update cost_generated_flag
        varOrder.setCostGeneratedFlag(1);
        varOrderMapper.updateById(varOrder);

        log.info("生成变更成本完成 varOrderId={}, 明细数={}, 新增={}", varOrderId, items.size(), generated);
    }

    private static void applyDecision(CostItem cost, CostSubjectResolver.Decision decision) {
        cost.setCostSubjectId(decision.costSubjectId());
        cost.setClassificationStatus(decision.classificationStatus());
        cost.setMappingVersionId(decision.mappingVersionId());
        cost.setAssignmentRuleId(decision.assignmentRuleId());
        cost.setOriginalCostSubjectId(decision.originalCostSubjectId());
        cost.setClassificationOverrideId(decision.overrideId());
        cost.setClassificationSnapshotId(decision.snapshotId());
    }

}
