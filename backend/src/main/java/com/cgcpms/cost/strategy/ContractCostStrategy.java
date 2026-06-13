package com.cgcpms.cost.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.cgcpms.common.util.BigDecimalUtils.nvl;

import java.time.LocalDate;
import java.util.List;

/**
 * Contract cost generation strategy (CT_CONTRACT).
 * Generates locked cost records from approved contract items.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractCostStrategy implements CostGenerationStrategy {

    private static final String SOURCE_TYPE_CONTRACT = "CT_CONTRACT";
    private static final String DEFAULT_COST_TYPE = "CONTRACT_LOCKED";
    private static final String COST_STATUS_CONFIRMED = "CONFIRMED";

    private final CtContractMapper contractMapper;
    private final CtContractItemMapper contractItemMapper;
    private final CostItemMapper costItemMapper;
    private final CostSubjectResolver costSubjectResolver;

    @Override
    public String supportSourceType() {
        return SOURCE_TYPE_CONTRACT;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateCost(Long sourceId) {
        Long contractId = sourceId;
        CtContract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            log.warn("生成锁定成本：合同不存在 contractId={}", contractId);
            return;
        }

        List<CtContractItem> items = contractItemMapper.selectList(
                new LambdaQueryWrapper<CtContractItem>()
                        .eq(CtContractItem::getContractId, contractId));

        if (items.isEmpty()) {
            log.info("生成锁定成本：合同无清单明细，跳过 contractId={}", contractId);
            return;
        }

        LocalDate today = LocalDate.now();

        // Resolve default cost subject for CONTRACT_LOCKED type
        Long defaultSubjectId = costSubjectResolver.resolveDefaultSubjectId(contract.getTenantId(), "合同");

        int generated = 0;
        for (CtContractItem item : items) {
            CostItem cost = new CostItem();
            cost.setTenantId(contract.getTenantId());
            cost.setOrgId(contract.getOrgId());
            cost.setProjectId(contract.getProjectId());
            cost.setContractId(contractId);
            cost.setPartnerId(contract.getPartnerId());
            cost.setCostType(DEFAULT_COST_TYPE);
            cost.setCostSubjectId(defaultSubjectId);
            cost.setAmount(nvl(item.getAmount()));
            cost.setTaxAmount(nvl(item.getTaxAmount()));
            cost.setAmountWithoutTax(nvl(item.getAmountWithoutTax()));
            cost.setSourceType(SOURCE_TYPE_CONTRACT);
            cost.setSourceId(contractId);
            cost.setSourceItemId(item.getId());
            cost.setCostDate(today);
            cost.setCostStatus(COST_STATUS_CONFIRMED);
            cost.setGeneratedFlag(1);

            try {
                costItemMapper.insert(cost);
                generated++;
            } catch (DuplicateKeyException e) {
                // uk_cost_source_item already present — idempotent skip.
                log.info("成本已存在，跳过 contractId={}, itemId={}", contractId, item.getId());
            }
        }
        // Update cost_generated_flag
        contract.setCostGeneratedFlag(1);
        contractMapper.updateById(contract);

        log.info("生成锁定成本完成 contractId={}, 明细数={}, 新增={}", contractId, items.size(), generated);
    }

}
