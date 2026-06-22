package com.cgcpms.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.settlement.constant.SettlementStatusConstants;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.StlSettlementItem;
import com.cgcpms.settlement.mapper.StlSettlementItemMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.cgcpms.settlement.constant.SettlementStatusConstants.APPROVAL_APPROVING;
import static com.cgcpms.settlement.constant.SettlementStatusConstants.APPROVAL_DRAFT;
import static com.cgcpms.settlement.constant.SettlementStatusConstants.SETTLEMENT_DRAFT;
import static com.cgcpms.settlement.constant.SettlementStatusConstants.STATUS_DRAFT;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 结算写操作服务 — 创建/更新/删除 + 明细管理 + 审批提交。
 * 依赖 StlSettlementQueryService 做只读汇总计算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StlSettlementWriteService {

    private static final BigDecimal DEFAULT_WARRANTY_RATE = new BigDecimal("0.05");

    private final StlSettlementMapper stlSettlementMapper;
    private final StlSettlementItemMapper stlSettlementItemMapper;
    private final CtContractMapper ctContractMapper;
    private final WorkflowEngine workflowEngine;
    private final StlSettlementQueryService queryService;

    // ================================================================
    // Create
    // ================================================================

    @Transactional
    public Long create(StlSettlement settlement) {
        Long tenantId = UserContext.getCurrentTenantId();
        settlement.setTenantId(tenantId);

        CtContract contract = validateAndGetContract(settlement.getContractId(), tenantId, settlement.getProjectId());

        // Prevent duplicate settlements for the same contract
        Long existingCount = stlSettlementMapper.selectCount(
            new LambdaQueryWrapper<StlSettlement>()
                .eq(StlSettlement::getTenantId, tenantId)
                .eq(StlSettlement::getContractId, settlement.getContractId()));
        if (existingCount > 0) {
            throw new BusinessException("STL_DUPLICATE_SETTLEMENT",
                    "该合同已存在结算单，不允许重复创建");
        }

        // Auto-generate settlement code: STL-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String prefix = "STL-" + today + "-";
        LambdaQueryWrapper<StlSettlement> codeWrapper = new LambdaQueryWrapper<>();
        codeWrapper.eq(StlSettlement::getTenantId, tenantId)
                .likeRight(StlSettlement::getSettlementCode, prefix)
                .orderByDesc(StlSettlement::getSettlementCode);
        Page<StlSettlement> page = new Page<>(0, 1);
        Page<StlSettlement> result = stlSettlementMapper.selectPage(page, codeWrapper);
        StlSettlement last = result.getRecords().isEmpty() ? null : result.getRecords().get(0);
        int seq = 1;
        if (last != null && last.getSettlementCode() != null && last.getSettlementCode().startsWith(prefix)) {
            try {
                seq = Integer.parseInt(last.getSettlementCode().substring(last.getSettlementCode().lastIndexOf('-') + 1)) + 1;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", last.getSettlementCode(), e);
            }
        }
        settlement.setSettlementCode(prefix + String.format("%03d", seq));

        // Default statuses
        if (settlement.getApprovalStatus() == null || settlement.getApprovalStatus().isBlank()) {
            settlement.setApprovalStatus(APPROVAL_DRAFT);
        }
        if (settlement.getStatus() == null || settlement.getStatus().isBlank()) {
            settlement.setStatus(STATUS_DRAFT);
        }
        if (settlement.getSettlementStatus() == null || settlement.getSettlementStatus().isBlank()) {
            settlement.setSettlementStatus(SETTLEMENT_DRAFT);
        }

        // Auto-compute amounts
        autoFillAmounts(settlement, contract);

        // Database-level UNIQUE constraint as safety net against TOCTOU race
        try {
            stlSettlementMapper.insert(settlement);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("STL_DUPLICATE_SETTLEMENT",
                    "该合同已存在结算单，不允许重复创建");
        }
        return settlement.getId();
    }

    // ================================================================
    // Update / Delete
    // ================================================================

    @Transactional
    public void update(StlSettlement settlement) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement existing = queryService.validateAndGetSettlement(settlement.getId());
        if (!APPROVAL_DRAFT.equals(existing.getApprovalStatus())) {
            throw new BusinessException("STL_SETTLEMENT_IN_APPROVAL", "结算单审批中或已审批，不可编辑");
        }

        if (settlement.getContractId() != null && !Objects.equals(settlement.getContractId(), existing.getContractId())) {
            validateAndGetContract(settlement.getContractId(), tenantId, existing.getProjectId());
        }

        CtContract contract = ctContractMapper.selectById(existing.getContractId());
        autoFillAmounts(settlement, contract);

        stlSettlementMapper.updateById(settlement);
    }

    @Transactional
    public void delete(Long id) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement existing = queryService.validateAndGetSettlement(id);
        if (!APPROVAL_DRAFT.equals(existing.getApprovalStatus())) {
            throw new BusinessException("STL_SETTLEMENT_IN_APPROVAL", "结算单审批中或已审批，不可删除");
        }

        stlSettlementItemMapper.delete(new LambdaQueryWrapper<StlSettlementItem>()
                .eq(StlSettlementItem::getTenantId, tenantId)
                .eq(StlSettlementItem::getSettlementId, id));
        stlSettlementMapper.deleteById(id);
    }

    // ================================================================
    // Items management
    // ================================================================

    @Transactional
    public void saveItems(Long settlementId, List<StlSettlementItem> items) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement settlement = queryService.validateAndGetSettlement(settlementId);
        if (!APPROVAL_DRAFT.equals(settlement.getApprovalStatus())) {
            throw new BusinessException("STL_SETTLEMENT_IN_APPROVAL", "结算单审批中或已审批，不可编辑");
        }

        stlSettlementItemMapper.delete(new LambdaQueryWrapper<StlSettlementItem>()
                .eq(StlSettlementItem::getTenantId, tenantId)
                .eq(StlSettlementItem::getSettlementId, settlementId));

        if (items != null) {
            for (StlSettlementItem item : items) {
                item.setSettlementId(settlementId);
                item.setTenantId(tenantId);
                item.setId(null);
                stlSettlementItemMapper.insert(item);
            }
        }
    }

    // ================================================================
    // Workflow
    // ================================================================

    @Transactional
    public void submitForApproval(Long settlementId) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement settlement = stlSettlementMapper.selectById(settlementId);
        if (settlement == null || !Objects.equals(settlement.getTenantId(), tenantId)) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        if (!APPROVAL_DRAFT.equals(settlement.getApprovalStatus())) {
            throw new BusinessException("STL_ALREADY_SUBMITTED", "结算单已提交审批，不可重复提交");
        }

        // Recompute amount snapshot before approval
        CtContract contract = ctContractMapper.selectById(settlement.getContractId());
        autoFillAmounts(settlement, contract);

        stlSettlementMapper.update(null, new LambdaUpdateWrapper<StlSettlement>()
                .eq(StlSettlement::getId, settlementId)
                .set(StlSettlement::getApprovalStatus, APPROVAL_APPROVING));

        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.submit(userId, username, tenantId,
                WorkflowBusinessTypes.SETTLEMENT,
                settlementId,
                settlement.getSettlementCode(),
                settlement.getFinalAmount(),
                settlement.getProjectId(),
                settlement.getContractId(),
                null, null, null);
    }

    // ================================================================
    // Private helpers
    // ================================================================

    private CtContract validateAndGetContract(Long contractId, Long tenantId, Long projectId) {
        if (contractId == null) {
            throw new BusinessException("CONTRACT_REQUIRED", "结算单必须关联合同");
        }
        CtContract contract = ctContractMapper.selectById(contractId);
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)) {
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        }
        if (projectId != null && !Objects.equals(contract.getProjectId(), projectId)) {
            throw new BusinessException("CROSS_PROJECT_NOT_ALLOWED", "结算单项目与合同项目不一致，不允许跨项目引用");
        }
        return contract;
    }

    private void autoFillAmounts(StlSettlement settlement, CtContract contract) {
        if (contract == null) return;

        Long tenantId = settlement.getTenantId() != null ? settlement.getTenantId() : UserContext.getCurrentTenantId();
        Long contractId = contract.getId();

        BigDecimal contractAmount = contract.getCurrentAmount() != null ? contract.getCurrentAmount() : BigDecimal.ZERO;
        settlement.setContractAmount(contractAmount);

        BigDecimal changeAmount = queryService.sumVarOrderConfirmed(tenantId, contractId);
        settlement.setChangeAmount(changeAmount);

        BigDecimal measuredAmount = queryService.sumSubMeasureApproved(tenantId, contractId);
        settlement.setMeasuredAmount(measuredAmount);

        BigDecimal paidAmount = queryService.sumPaidAmount(tenantId, contractId);
        settlement.setPaidAmount(paidAmount);

        BigDecimal deductionAmount = settlement.getDeductionAmount() != null ? settlement.getDeductionAmount() : BigDecimal.ZERO;
        settlement.setDeductionAmount(deductionAmount);

        BigDecimal finalAmount = contractAmount.add(changeAmount).add(measuredAmount).subtract(deductionAmount);
        settlement.setFinalAmount(finalAmount);

        BigDecimal warrantyRate = DEFAULT_WARRANTY_RATE;
        BigDecimal warrantyAmount = finalAmount.multiply(warrantyRate).setScale(2, RoundingMode.HALF_UP);
        settlement.setWarrantyAmount(warrantyAmount);

        BigDecimal unpaidAmount = finalAmount.subtract(paidAmount).subtract(warrantyAmount);
        settlement.setUnpaidAmount(unpaidAmount);
    }
}
