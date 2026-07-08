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

/**
 * Material requisition cost generation strategy (MAT_REQUISITION).
 * Generates material cost records from approved requisition items.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialRequisitionCostStrategy implements CostGenerationStrategy {

    private static final String SOURCE_TYPE = "MAT_REQUISITION";
    private static final String COST_TYPE = "MATERIAL";
    private static final String COST_STATUS_CONFIRMED = "CONFIRMED";

    private final MatRequisitionMapper requisitionMapper;
    private final MatRequisitionItemMapper requisitionItemMapper;
    private final CostItemMapper costItemMapper;
    private final CostSubjectResolver costSubjectResolver;

    @Override
    public String supportSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateCost(Long sourceId) {
        Long requisitionId = sourceId;
        MatRequisition requisition = requisitionMapper.selectById(requisitionId);
        if (requisition == null) {
            log.warn("生成领料成本：领料申请不存在 requisitionId={}", requisitionId);
            return;
        }

        List<MatRequisitionItem> items = requisitionItemMapper.selectList(
                new LambdaQueryWrapper<MatRequisitionItem>()
                        .eq(MatRequisitionItem::getRequisitionId, requisitionId));
        if (items.isEmpty()) {
            log.info("生成领料成本：领料申请无明细，跳过 requisitionId={}", requisitionId);
            return;
        }

        Long defaultSubjectId = costSubjectResolver.resolveDefaultSubjectId(requisition.getTenantId(), "材料");
        LocalDate today = LocalDate.now();
        int generated = 0;
        for (MatRequisitionItem item : items) {
            CostItem cost = new CostItem();
            cost.setTenantId(requisition.getTenantId());
            cost.setProjectId(requisition.getProjectId());
            cost.setContractId(requisition.getContractId());
            cost.setPartnerId(requisition.getPartnerId());
            cost.setCostType(COST_TYPE);
            cost.setCostSubjectId(defaultSubjectId);
            cost.setAmount(nvl(item.getAmount()));
            cost.setTaxAmount(BigDecimal.ZERO);
            cost.setAmountWithoutTax(nvl(item.getAmount()));
            cost.setSourceType(SOURCE_TYPE);
            cost.setSourceId(requisitionId);
            cost.setSourceItemId(item.getId());
            cost.setCostDate(today);
            cost.setCostStatus(COST_STATUS_CONFIRMED);
            cost.setGeneratedFlag(1);

            try {
                costItemMapper.insert(cost);
                generated++;
            } catch (DuplicateKeyException e) {
                log.info("领料成本已存在，跳过 requisitionId={}, itemId={}", requisitionId, item.getId());
            }
        }

        log.info("生成领料成本完成 requisitionId={}, 明细数={}, 新增={}", requisitionId, items.size(), generated);
    }
}
