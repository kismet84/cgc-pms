package com.cgcpms.cost.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Generates locked cost records (cost_item) from an approved contract's items.
 * Idempotent: relies on the unique key uk_cost_source_item to skip duplicates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostGenerationService {

    /** Source type marking the cost as originating from a contract. */
    public static final String SOURCE_TYPE_CONTRACT = "CT_CONTRACT";

    /** Default cost type for contract-locked costs (no per-item classification yet). */
    public static final String DEFAULT_COST_TYPE = "CONTRACT_LOCKED";

    /** Cost status once locked from an approved contract. */
    public static final String COST_STATUS_CONFIRMED = "CONFIRMED";

    private final CtContractMapper contractMapper;
    private final CtContractItemMapper contractItemMapper;
    private final CostItemMapper costItemMapper;

    /**
     * Generate locked cost records for every line item of the given contract.
     * Idempotent — re-running on the same contract skips already-generated rows.
     *
     * @param contractId the approved contract id
     */
    @Transactional(rollbackFor = Exception.class)
    public void generateLockedCost(Long contractId) {
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
        int generated = 0;
        for (CtContractItem item : items) {
            CostItem cost = new CostItem();
            cost.setTenantId(contract.getTenantId());
            cost.setOrgId(contract.getOrgId());
            cost.setProjectId(contract.getProjectId());
            cost.setContractId(contractId);
            cost.setPartnerId(contract.getPartnerId());
            cost.setCostType(DEFAULT_COST_TYPE);
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
        log.info("生成锁定成本完成 contractId={}, 明细数={}, 新增={}", contractId, items.size(), generated);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
