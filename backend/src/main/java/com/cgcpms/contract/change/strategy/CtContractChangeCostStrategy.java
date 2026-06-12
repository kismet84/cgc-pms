package com.cgcpms.contract.change.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.strategy.CostGenerationStrategy;
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
    private final CostSubjectMapper costSubjectMapper;

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

        // Look up the associated contract for partner/org info
        CtContract contract = contractMapper.selectById(change.getContractId());

        // Resolve cost subject: try "变更" type → "合同" type → root → any
        Long costSubjectId = resolveCostSubjectId(change.getTenantId());

        LocalDate today = LocalDate.now();

        CostItem cost = new CostItem();
        cost.setTenantId(change.getTenantId());
        cost.setOrgId(contract != null ? contract.getOrgId() : null);
        cost.setProjectId(change.getProjectId());
        cost.setContractId(change.getContractId());
        cost.setPartnerId(contract != null ? contract.getPartnerId() : null);
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
            log.info("成本已存在，跳过 changeId={}", changeId);
            // Still mark flag so we don't retry forever
            if (!Integer.valueOf(1).equals(change.getCostGeneratedFlag())) {
                change.setCostGeneratedFlag(1);
                changeMapper.updateById(change);
            }
            return;
        }

        // Update cost_generated_flag
        change.setCostGeneratedFlag(1);
        changeMapper.updateById(change);

        log.info("生成变更成本完成 changeId={}, amount={}", changeId, change.getChangeAmount());
    }

    /**
     * Resolve a cost_subject_id for CT_CHANGE.
     * Tries "变更" subject_type first, then "合同", then root-level, then any enabled subject.
     */
    private Long resolveCostSubjectId(Long tenantId) {
        // 1. Try "变更" subject type
        Long id = findSubjectByType(tenantId, "变更");
        if (id != null) return id;

        // 2. Fallback: "合同" subject type (contract default)
        id = findSubjectByType(tenantId, "合同");
        if (id != null) {
            log.warn("未找到 subject_type=变更 对应的科目，使用合同科目 subjectId={}", id);
            return id;
        }

        // 3. Fallback: root-level subject
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, tenantId);
        wrapper.eq(CostSubject::getParentId, 0L);
        wrapper.eq(CostSubject::getStatus, "ENABLE");
        wrapper.eq(CostSubject::getDeletedFlag, 0);
        wrapper.orderByAsc(CostSubject::getSortOrder);
        wrapper.last("LIMIT 1");
        CostSubject subject = costSubjectMapper.selectOne(wrapper);
        if (subject != null) {
            log.warn("未找到 subject_type=变更/合同 对应的科目，使用根科目 subjectId={}", subject.getId());
            return subject.getId();
        }

        // 4. Last resort: any enabled subject
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, tenantId);
        wrapper.eq(CostSubject::getStatus, "ENABLE");
        wrapper.eq(CostSubject::getDeletedFlag, 0);
        wrapper.orderByAsc(CostSubject::getLevel, CostSubject::getSortOrder);
        wrapper.last("LIMIT 1");
        subject = costSubjectMapper.selectOne(wrapper);
        if (subject != null) {
            log.warn("未找到 subject_type=变更/合同 对应的科目且无根科目，使用第一个可用科目 subjectId={}", subject.getId());
            return subject.getId();
        }

        log.error("租户 {} 下无任何启用科目，costSubjectId 将为 null，source_type=CT_CHANGE", tenantId);
        return null;
    }

    private Long findSubjectByType(Long tenantId, String subjectType) {
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, tenantId);
        wrapper.eq(CostSubject::getSubjectType, subjectType);
        wrapper.eq(CostSubject::getStatus, "ENABLE");
        wrapper.eq(CostSubject::getDeletedFlag, 0);
        wrapper.orderByAsc(CostSubject::getLevel);
        wrapper.last("LIMIT 1");
        CostSubject subject = costSubjectMapper.selectOne(wrapper);
        return subject != null ? subject.getId() : null;
    }
}
