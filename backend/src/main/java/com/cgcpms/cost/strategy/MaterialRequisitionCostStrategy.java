package com.cgcpms.cost.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        Long subjectId = costSubjectResolver.resolveDefaultSubjectId(requisition.getTenantId(), "材料");
        LocalDate costDate = requisition.getRequisitionDate() != null
                ? requisition.getRequisitionDate() : LocalDate.now();
        for (MatRequisitionItem item : items) {
            if (nvl(item.getAmount()).signum() <= 0) continue;
            CostItem cost = new CostItem();
            cost.setTenantId(requisition.getTenantId());
            cost.setProjectId(requisition.getProjectId());
            cost.setContractId(requisition.getContractId());
            cost.setPartnerId(requisition.getPartnerId());
            cost.setCostType("MATERIAL");
            cost.setCostSubjectId(subjectId);
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
            } catch (DuplicateKeyException ignored) {
                log.info("领料成本已存在，跳过 requisitionId={}, itemId={}", requisitionId, item.getId());
            }
        }
    }
}
