package com.cgcpms.contract.change.strategy;

import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.strategy.CostGenerationStrategy;
import com.cgcpms.cost.strategy.CostSubjectResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.cgcpms.common.util.BigDecimalUtils.nvl;

import java.time.LocalDate;

/**
 * Contract change cost generation strategy (CT_CHANGE).
 * Generates a cost record from an approved + effective contract change.
 * Single-entity source (no line items) — one CostItem per change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CtContractChangeCostStrategy implements CostGenerationStrategy {

    private static final String SOURCE_TYPE = "CT_CHANGE";
    private static final String COST_TYPE = "CHANGE";
    private static final String COST_STATUS_CONFIRMED = "CONFIRMED";
    private static final String APPROVAL_STATUS_APPROVED = "APPROVED";

    private final CtContractChangeMapper changeMapper;
    private final CtContractMapper contractMapper;
    private final CostItemMapper costItemMapper;
    private final CostSubjectResolver costSubjectResolver;

    @Override
    public String supportSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateCost(Long sourceId) {
        Long changeId = sourceId;
        CtContractChange change = changeMapper.selectById(changeId);
        if (change == null) {
            log.warn("生成变更成本：变更单不存在 changeId={}", changeId);
            return;
        }

        // Only generate cost when approved AND effective
        if (!APPROVAL_STATUS_APPROVED.equals(change.getApprovalStatus())) {
            log.info("生成变更成本：审批状态非 APPROVED，跳过 changeId={}, approvalStatus={}",
                    changeId, change.getApprovalStatus());
            return;
        }
        if (!Integer.valueOf(1).equals(change.getEffectiveFlag())) {
            log.info("生成变更成本：未生效，跳过 changeId={}, effectiveFlag={}",
                    changeId, change.getEffectiveFlag());
            return;
        }

        // 由变更签证业主核定自动生成的合同变更，成本已经在签证内部审批时入账，禁止重复生成。
        if (change.getSourceVarOrderId() != null) {
            change.setCostGeneratedFlag(1);
            changeMapper.updateById(change);
            log.info("来源签证的合同变更跳过重复成本生成 changeId={}, varOrderId={}", changeId, change.getSourceVarOrderId());
            return;
        }

        // Look up the associated contract for partner/org info
        CtContract contract = contractMapper.selectById(change.getContractId());

        // Resolve cost subject: try "变更" type → "合同" type → root → any
        Long costSubjectId = costSubjectResolver.resolveForChange(change.getTenantId());

        LocalDate today = LocalDate.now();

        CostItem cost = new CostItem();
        cost.setTenantId(change.getTenantId());
        cost.setOrgId(contract != null ? contract.getOrgId() : null);
        cost.setProjectId(change.getProjectId());
        cost.setContractId(change.getContractId());
        cost.setPartnerId(contract != null ? null /* partnerId removed — use partyAId/partyBId */ : null);
        cost.setCostType(COST_TYPE);
        cost.setCostSubjectId(costSubjectId);
        cost.setAmount(nvl(change.getChangeAmount()));
        cost.setSourceType(SOURCE_TYPE);
        cost.setSourceId(changeId);
        cost.setSourceItemId(null);
        cost.setCostDate(today);
        cost.setCostStatus(COST_STATUS_CONFIRMED);
        cost.setGeneratedFlag(1);

        try {
            costItemMapper.insert(cost);
        } catch (DuplicateKeyException e) {
            // uk_cost_source_item already present — idempotent skip.
            // Re-query to get the current costGeneratedFlag (may have been set by another thread).
            CtContractChange fresh = changeMapper.selectById(changeId);
            if (fresh != null && Integer.valueOf(1).equals(fresh.getCostGeneratedFlag())) {
                log.info("成本已存在且 flag 已置，幂等跳过 changeId={}", changeId);
                return;
            }
            // costGeneratedFlag is NOT set, but a duplicate record exists.
            // This is a real conflict — throw to roll back the transaction.
            log.warn("DuplicateKeyException 但 costGeneratedFlag 未置为 1，回滚事务 changeId={}", changeId, e);
            throw new RuntimeException(
                    "Cost generation conflict: duplicate key but costGeneratedFlag not set for changeId=" + changeId, e);
        }

        // Update cost_generated_flag
        change.setCostGeneratedFlag(1);
        changeMapper.updateById(change);
        log.info("生成变更成本完成 changeId={}, amount={}", changeId, change.getChangeAmount());
    }

}
