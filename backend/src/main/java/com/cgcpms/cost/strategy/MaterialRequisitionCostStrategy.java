package com.cgcpms.cost.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.mapper.MatRequisitionItemMapper;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.cgcpms.common.util.BigDecimalUtils.nvl;

/** 按实际出库移动加权平均价值生成项目材料成本。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialRequisitionCostStrategy implements CostGenerationStrategy {

    private final MatRequisitionMapper requisitionMapper;
    private final MatRequisitionItemMapper requisitionItemMapper;
    private final CostItemMapper costItemMapper;
    private final CostSubjectResolver costSubjectResolver;
    private final AccountingPeriodGuard accountingPeriodGuard;

    @Override
    public String supportSourceType() {
        return "MAT_REQUISITION";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateCost(Long requisitionId) {
        MatRequisition requisition = requisitionMapper.selectById(requisitionId);
        if (requisition == null) return;
        List<MatRequisitionItem> items = requisitionItemMapper.selectList(
                new LambdaQueryWrapper<MatRequisitionItem>()
                        .eq(MatRequisitionItem::getTenantId, requisition.getTenantId())
                        .eq(MatRequisitionItem::getRequisitionId, requisitionId));
        LocalDate costDate = requisition.getRequisitionDate() != null
                ? requisition.getRequisitionDate() : LocalDate.now();
        accountingPeriodGuard.assertWritable(costDate);
        for (MatRequisitionItem item : items) {
            if (nvl(item.getAmount()).signum() <= 0) continue;
            if (item.getWbsTaskId() == null) {
                throw new IllegalStateException("领料明细未关联WBS任务，禁止生成成本 itemId=" + item.getId());
            }
            if (costSubjectResolver.costFactExists(requisition.getTenantId(), "MAT_REQUISITION",
                    requisitionId, item.getId(), "MATERIAL")) {
                continue;
            }
            CostItem cost = new CostItem();
            cost.setTenantId(requisition.getTenantId());
            cost.setProjectId(requisition.getProjectId());
            cost.setWbsTaskId(item.getWbsTaskId());
            cost.setContractId(requisition.getContractId());
            cost.setPartnerId(requisition.getPartnerId());
            cost.setCostType("MATERIAL");
            CostSubjectResolver.Decision decision = costSubjectResolver.resolveForFact(
                    requisition.getTenantId(), requisition.getProjectId(), "MAT_REQUISITION",
                    "*", requisitionId, item.getId(), null, costDate);
            applyDecision(cost, decision);
            cost.setClassificationBusinessCategory("*");
            cost.setAmount(nvl(item.getAmount()));
            cost.setTaxAmount(BigDecimal.ZERO);
            cost.setAmountWithoutTax(nvl(item.getAmount()));
            cost.setSourceType("MAT_REQUISITION");
            cost.setSourceId(requisitionId);
            cost.setSourceItemId(item.getId());
            cost.setCostDate(costDate);
            cost.setCostStatus("CONFIRMED");
            cost.setGeneratedFlag(1);
            try {
                costItemMapper.insert(cost);
                costSubjectResolver.markSnapshotPosted(decision);
            } catch (DuplicateKeyException ignored) {
                costSubjectResolver.markSnapshotPosted(decision);
                log.info("领料成本已存在，跳过 requisitionId={}, itemId={}", requisitionId, item.getId());
            }
        }
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
