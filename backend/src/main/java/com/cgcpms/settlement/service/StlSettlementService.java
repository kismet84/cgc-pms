package com.cgcpms.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.StlSettlementItem;
import com.cgcpms.settlement.mapper.StlSettlementItemMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.vo.StlSettlementItemVO;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 结算服务 — 纯只读汇总逻辑。
 * 结算不生成成本 item，审批通过后仅锁定金额并回写合同。
 * 禁止调用 CostGenerationService（防循环依赖）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StlSettlementService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final BigDecimal DEFAULT_WARRANTY_RATE = new BigDecimal("0.05");

    private final StlSettlementMapper stlSettlementMapper;
    private final StlSettlementItemMapper stlSettlementItemMapper;
    private final CtContractMapper ctContractMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final VarOrderMapper varOrderMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final PayRecordMapper payRecordMapper;

    // ================================================================
    // Query
    // ================================================================

    public IPage<StlSettlementVO> getPage(long pageNo, long pageSize, Long projectId, Long contractId,
                                          Long partnerId, String settlementCode, String settlementType) {
        Long tenantId = UserContext.getCurrentTenantId();
        LambdaQueryWrapper<StlSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StlSettlement::getTenantId, tenantId);
        if (projectId != null) wrapper.eq(StlSettlement::getProjectId, projectId);
        if (contractId != null) wrapper.eq(StlSettlement::getContractId, contractId);
        if (partnerId != null) wrapper.eq(StlSettlement::getPartnerId, partnerId);
        if (StringUtils.hasText(settlementCode)) wrapper.like(StlSettlement::getSettlementCode, settlementCode);
        if (StringUtils.hasText(settlementType)) wrapper.eq(StlSettlement::getSettlementType, settlementType);
        wrapper.orderByDesc(StlSettlement::getCreatedAt);

        Page<StlSettlement> page = stlSettlementMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(m -> toVO(m, resolveNameMaps(page.getRecords())));
    }

    public StlSettlementVO getById(Long id) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement settlement = stlSettlementMapper.selectById(id);
        if (settlement == null || !Objects.equals(settlement.getTenantId(), tenantId)) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }

        StlSettlementVO vo = toVO(settlement);

        // Load items
        List<StlSettlementItem> items = stlSettlementItemMapper.selectList(
                new LambdaQueryWrapper<StlSettlementItem>()
                        .eq(StlSettlementItem::getTenantId, tenantId)
                        .eq(StlSettlementItem::getSettlementId, id));
        vo.setItems(items.stream().map(this::toItemVO).collect(Collectors.toList()));

        return vo;
    }

    // ================================================================
    // Create / Update / Delete
    // ================================================================

    @Transactional
    public Long create(StlSettlement settlement) {
        Long tenantId = UserContext.getCurrentTenantId();
        settlement.setTenantId(tenantId);

        // Validate contract exists and belongs to same tenant + same project
        CtContract contract = validateAndGetContract(settlement.getContractId(), tenantId, settlement.getProjectId());

        // Auto-generate settlement code: STL-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "STL-" + today + "-";
        LambdaQueryWrapper<StlSettlement> codeWrapper = new LambdaQueryWrapper<>();
        codeWrapper.eq(StlSettlement::getTenantId, tenantId)
                .likeRight(StlSettlement::getSettlementCode, prefix)
                .orderByDesc(StlSettlement::getSettlementCode)
                .last("LIMIT 1");
        StlSettlement last = stlSettlementMapper.selectOne(codeWrapper);
        int seq = 1;
        if (last != null && last.getSettlementCode() != null && last.getSettlementCode().startsWith(prefix)) {
            try {
                seq = Integer.parseInt(last.getSettlementCode().substring(last.getSettlementCode().lastIndexOf('-') + 1)) + 1;
            } catch (NumberFormatException ignored) {
            }
        }
        settlement.setSettlementCode(prefix + String.format("%03d", seq));

        // Default statuses
        if (settlement.getApprovalStatus() == null || settlement.getApprovalStatus().isBlank()) {
            settlement.setApprovalStatus("DRAFT");
        }
        if (settlement.getStatus() == null || settlement.getStatus().isBlank()) {
            settlement.setStatus("DRAFT");
        }
        if (settlement.getSettlementStatus() == null || settlement.getSettlementStatus().isBlank()) {
            settlement.setSettlementStatus("DRAFT");
        }

        // Auto-compute amounts (NEVER allow manual override)
        autoFillAmounts(settlement, contract);

        stlSettlementMapper.insert(settlement);
        return settlement.getId();
    }

    @Transactional
    public void update(StlSettlement settlement) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement existing = stlSettlementMapper.selectById(settlement.getId());
        if (existing == null || !Objects.equals(existing.getTenantId(), tenantId)) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        if (!"DRAFT".equals(existing.getApprovalStatus())) {
            throw new BusinessException("STL_SETTLEMENT_IN_APPROVAL", "结算单审批中或已审批，不可编辑");
        }

        // Validate contract if changed
        if (settlement.getContractId() != null && !Objects.equals(settlement.getContractId(), existing.getContractId())) {
            validateAndGetContract(settlement.getContractId(), tenantId, existing.getProjectId());
        }

        // Always re-compute amounts on update — forbid manual override
        CtContract contract = ctContractMapper.selectById(existing.getContractId());
        autoFillAmounts(settlement, contract);

        stlSettlementMapper.updateById(settlement);
    }

    @Transactional
    public void delete(Long id) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement existing = stlSettlementMapper.selectById(id);
        if (existing == null || !Objects.equals(existing.getTenantId(), tenantId)) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        if (!"DRAFT".equals(existing.getApprovalStatus())) {
            throw new BusinessException("STL_SETTLEMENT_IN_APPROVAL", "结算单审批中或已审批，不可删除");
        }

        // Delete items first
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
        StlSettlement settlement = stlSettlementMapper.selectById(settlementId);
        if (settlement == null || !Objects.equals(settlement.getTenantId(), tenantId)) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        if (!"DRAFT".equals(settlement.getApprovalStatus())) {
            throw new BusinessException("STL_SETTLEMENT_IN_APPROVAL", "结算单审批中或已审批，不可编辑");
        }

        // Delete old items
        stlSettlementItemMapper.delete(new LambdaQueryWrapper<StlSettlementItem>()
                .eq(StlSettlementItem::getTenantId, tenantId)
                .eq(StlSettlementItem::getSettlementId, settlementId));

        // Batch insert new items
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
    // Pure read-only compute — NEVER calls CostGenerationService
    // ================================================================

    /**
     * 纯只读汇总计算：基于合同 + 变更 + 计量 + 付款记录计算结算金额。
     * 此方法绝不调用 CostGenerationService，防循环依赖。
     */
    public StlSettlementVO computeSettlementAmount(Long contractId) {
        Long tenantId = UserContext.getCurrentTenantId();

        CtContract contract = ctContractMapper.selectById(contractId);
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)) {
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        }

        BigDecimal contractAmount = contract.getCurrentAmount() != null ? contract.getCurrentAmount() : BigDecimal.ZERO;

        // SUM(confirmed varOrders COST方向, ownerConfirmFlag=1)
        BigDecimal changeAmount = sumVarOrderConfirmed(tenantId, contractId);

        // SUM(confirmed measurements, approvalStatus=APPROVED)
        BigDecimal measuredAmount = sumSubMeasureApproved(tenantId, contractId);

        // Total paid amount for this contract
        BigDecimal paidAmount = sumPaidAmount(tenantId, contractId);

        // Deduction amount — from existing settlement data or zero for compute-only
        BigDecimal deductionAmount = BigDecimal.ZERO;

        // finalAmount
        BigDecimal finalAmount = contractAmount.add(changeAmount).add(measuredAmount).subtract(deductionAmount);

        // warrantyRate from contract, default 5%
        BigDecimal warrantyRate = contract.getWarrantyRate() != null ? contract.getWarrantyRate() : DEFAULT_WARRANTY_RATE;
        BigDecimal warrantyAmount = finalAmount.multiply(warrantyRate).setScale(2, RoundingMode.HALF_UP);

        // unpaidAmount = finalAmount - paidAmount - warrantyAmount
        BigDecimal unpaidAmount = finalAmount.subtract(paidAmount).subtract(warrantyAmount);

        StlSettlementVO vo = new StlSettlementVO();
        vo.setContractAmount(contractAmount.toPlainString());
        vo.setChangeAmount(changeAmount.toPlainString());
        vo.setMeasuredAmount(measuredAmount.toPlainString());
        vo.setDeductionAmount(deductionAmount.toPlainString());
        vo.setPaidAmount(paidAmount.toPlainString());
        vo.setFinalAmount(finalAmount.toPlainString());
        vo.setWarrantyAmount(warrantyAmount.toPlainString());
        vo.setUnpaidAmount(unpaidAmount.toPlainString());
        vo.setContractName(contract.getContractName());
        vo.setProjectId(contract.getProjectId() != null ? contract.getProjectId().toString() : null);
        return vo;
    }

    // ---- Pure read-only helpers (no write, no CostGenerationService) ----

    private BigDecimal sumVarOrderConfirmed(Long tenantId, Long contractId) {
        LambdaQueryWrapper<VarOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VarOrder::getTenantId, tenantId);
        wrapper.eq(VarOrder::getContractId, contractId);
        wrapper.eq(VarOrder::getDirection, "COST");
        wrapper.eq(VarOrder::getOwnerConfirmFlag, 1);
        List<VarOrder> orders = varOrderMapper.selectList(wrapper);
        return orders.stream()
                .map(v -> v.getConfirmedAmount() != null ? v.getConfirmedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumSubMeasureApproved(Long tenantId, Long contractId) {
        LambdaQueryWrapper<SubMeasure> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubMeasure::getTenantId, tenantId);
        wrapper.eq(SubMeasure::getContractId, contractId);
        wrapper.eq(SubMeasure::getApprovalStatus, "APPROVED");
        List<SubMeasure> measures = subMeasureMapper.selectList(wrapper);
        return measures.stream()
                .map(m -> m.getApprovedAmount() != null ? m.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPaidAmount(Long tenantId, Long contractId) {
        LambdaQueryWrapper<PayRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayRecord::getTenantId, tenantId);
        wrapper.eq(PayRecord::getContractId, contractId);
        wrapper.eq(PayRecord::getPayStatus, "SUCCESS");
        List<PayRecord> records = payRecordMapper.selectList(wrapper);
        return records.stream()
                .map(r -> r.getPayAmount() != null ? r.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---- Amount auto-fill on create/update ----

    private void autoFillAmounts(StlSettlement settlement, CtContract contract) {
        if (contract == null) return;

        Long tenantId = settlement.getTenantId() != null ? settlement.getTenantId() : UserContext.getCurrentTenantId();
        Long contractId = contract.getId();

        // contractAmount: snapshot from contract
        BigDecimal contractAmount = contract.getCurrentAmount() != null ? contract.getCurrentAmount() : BigDecimal.ZERO;
        settlement.setContractAmount(contractAmount);

        // changeAmount: SUM confirmed varOrders
        BigDecimal changeAmount = sumVarOrderConfirmed(tenantId, contractId);
        settlement.setChangeAmount(changeAmount);

        // measuredAmount: SUM approved sub measures
        BigDecimal measuredAmount = sumSubMeasureApproved(tenantId, contractId);
        settlement.setMeasuredAmount(measuredAmount);

        // paidAmount: SUM successful pay records
        BigDecimal paidAmount = sumPaidAmount(tenantId, contractId);
        settlement.setPaidAmount(paidAmount);

        // deductionAmount: keep existing or default 0
        BigDecimal deductionAmount = settlement.getDeductionAmount() != null ? settlement.getDeductionAmount() : BigDecimal.ZERO;
        settlement.setDeductionAmount(deductionAmount);

        // finalAmount = contractAmount + changeAmount + measuredAmount - deductionAmount
        BigDecimal finalAmount = contractAmount.add(changeAmount).add(measuredAmount).subtract(deductionAmount);
        settlement.setFinalAmount(finalAmount);

        // warrantyAmount = finalAmount × warrantyRate (default 5%)
        BigDecimal warrantyRate = contract.getWarrantyRate() != null ? contract.getWarrantyRate() : DEFAULT_WARRANTY_RATE;
        BigDecimal warrantyAmount = finalAmount.multiply(warrantyRate).setScale(2, RoundingMode.HALF_UP);
        settlement.setWarrantyAmount(warrantyAmount);

        // unpaidAmount = finalAmount - paidAmount - warrantyAmount
        BigDecimal unpaidAmount = finalAmount.subtract(paidAmount).subtract(warrantyAmount);
        settlement.setUnpaidAmount(unpaidAmount);
    }

    // ---- Validation helpers ----

    /**
     * Validates contract exists, belongs to tenant, and matches project (no cross-project).
     */
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

    // ================================================================
    // VO conversion
    // ================================================================

    private NameMaps resolveNameMaps(List<StlSettlement> records) {
        Set<Long> projectIds = records.stream().map(StlSettlement::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> contractIds = records.stream().map(StlSettlement::getContractId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> partnerIds = records.stream().map(StlSettlement::getPartnerId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> projectNames = projectIds.isEmpty() ? Collections.emptyMap()
                : pmProjectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> contractNames = contractIds.isEmpty() ? Collections.emptyMap()
                : ctContractMapper.selectBatchIds(contractIds).stream()
                .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Collections.emptyMap()
                : mdPartnerMapper.selectBatchIds(partnerIds).stream()
                .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));

        return new NameMaps(projectNames, contractNames, partnerNames);
    }

    private StlSettlementVO toVO(StlSettlement m) {
        NameMaps maps = resolveNameMaps(List.of(m));
        return toVO(m, maps);
    }

    private StlSettlementVO toVO(StlSettlement m, NameMaps maps) {
        StlSettlementVO vo = new StlSettlementVO();
        vo.setId(m.getId() != null ? m.getId().toString() : null);
        vo.setTenantId(m.getTenantId() != null ? m.getTenantId().toString() : null);
        vo.setProjectId(m.getProjectId() != null ? m.getProjectId().toString() : null);
        vo.setContractId(m.getContractId() != null ? m.getContractId().toString() : null);
        vo.setPartnerId(m.getPartnerId() != null ? m.getPartnerId().toString() : null);
        vo.setSettlementCode(m.getSettlementCode());
        vo.setSettlementType(m.getSettlementType());
        vo.setContractAmount(m.getContractAmount() != null ? m.getContractAmount().toPlainString() : null);
        vo.setChangeAmount(m.getChangeAmount() != null ? m.getChangeAmount().toPlainString() : null);
        vo.setMeasuredAmount(m.getMeasuredAmount() != null ? m.getMeasuredAmount().toPlainString() : null);
        vo.setDeductionAmount(m.getDeductionAmount() != null ? m.getDeductionAmount().toPlainString() : null);
        vo.setPaidAmount(m.getPaidAmount() != null ? m.getPaidAmount().toPlainString() : null);
        vo.setFinalAmount(m.getFinalAmount() != null ? m.getFinalAmount().toPlainString() : null);
        vo.setApprovalStatus(m.getApprovalStatus());
        vo.setStatus(m.getStatus());
        vo.setUnpaidAmount(m.getUnpaidAmount() != null ? m.getUnpaidAmount().toPlainString() : null);
        vo.setWarrantyAmount(m.getWarrantyAmount() != null ? m.getWarrantyAmount().toPlainString() : null);
        vo.setSettlementStatus(m.getSettlementStatus());
        vo.setFinalizedAt(m.getFinalizedAt() != null ? m.getFinalizedAt().format(DTF) : null);
        vo.setProjectName(maps.projectNames().get(m.getProjectId()));
        vo.setContractName(maps.contractNames().get(m.getContractId()));
        vo.setPartnerName(maps.partnerNames().get(m.getPartnerId()));
        vo.setCreatedBy(m.getCreatedBy() != null ? m.getCreatedBy().toString() : null);
        vo.setCreatedAt(m.getCreatedAt() != null ? m.getCreatedAt().format(DTF) : null);
        vo.setUpdatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt().format(DTF) : null);
        vo.setRemark(m.getRemark());
        return vo;
    }

    private StlSettlementItemVO toItemVO(StlSettlementItem item) {
        StlSettlementItemVO vo = new StlSettlementItemVO();
        vo.setId(item.getId() != null ? item.getId().toString() : null);
        vo.setTenantId(item.getTenantId() != null ? item.getTenantId().toString() : null);
        vo.setSettlementId(item.getSettlementId() != null ? item.getSettlementId().toString() : null);
        vo.setItemName(item.getItemName());
        vo.setUnit(item.getUnit());
        vo.setQuantity(item.getQuantity() != null ? item.getQuantity().toPlainString() : null);
        vo.setUnitPrice(item.getUnitPrice() != null ? item.getUnitPrice().toPlainString() : null);
        vo.setAmount(item.getAmount() != null ? item.getAmount().toPlainString() : null);
        vo.setCostSubjectId(item.getCostSubjectId() != null ? item.getCostSubjectId().toString() : null);
        vo.setSourceType(item.getSourceType());
        vo.setSourceId(item.getSourceId() != null ? item.getSourceId().toString() : null);
        vo.setCreatedBy(item.getCreatedBy() != null ? item.getCreatedBy().toString() : null);
        vo.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().format(DTF) : null);
        vo.setUpdatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().format(DTF) : null);
        vo.setRemark(item.getRemark());
        return vo;
    }

    // ---- Internal helper record ----

    private record NameMaps(Map<Long, String> projectNames,
                            Map<Long, String> contractNames,
                            Map<Long, String> partnerNames) {
    }
}
