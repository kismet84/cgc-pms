package com.cgcpms.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
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
import com.cgcpms.settlement.vo.SettlementApprovalRecordVO;
import com.cgcpms.settlement.vo.SettlementSourcesVO;
import com.cgcpms.settlement.vo.StlSettlementItemVO;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfRecord;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfRecordMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 结算服务 — 纯只读汇总逻辑。
 * 结算不生成成本 item，审批通过后仅锁定金额并回写合同。
 * 禁止调用 CostGenerationService（防循环依赖）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
// TODO: 拆分超大文件 (744行) — 拆分为 StlSettlementQueryService + StlSettlementWriteService + StlSettlementAssembler
public class StlSettlementService {

    private static final BigDecimal DEFAULT_WARRANTY_RATE = new BigDecimal("0.05");

    private final StlSettlementMapper stlSettlementMapper;
    private final StlSettlementItemMapper stlSettlementItemMapper;
    private final CtContractMapper ctContractMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final VarOrderMapper varOrderMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final PayRecordMapper payRecordMapper;
    private final CostItemMapper costItemMapper;
    private final SysFileMapper sysFileMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfRecordMapper wfRecordMapper;
    private final WorkflowEngine workflowEngine;

    // ================================================================
    // Query
    // ================================================================

    public IPage<StlSettlementVO> getPage(long pageNo, long pageSize, Long projectId, Long contractId,
                                          Long partnerId, String settlementCode, String settlementType,
                                          String keyword) {
        Long tenantId = UserContext.getCurrentTenantId();
        LambdaQueryWrapper<StlSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StlSettlement::getTenantId, tenantId);
        if (projectId != null) wrapper.eq(StlSettlement::getProjectId, projectId);
        if (contractId != null) wrapper.eq(StlSettlement::getContractId, contractId);
        if (partnerId != null) wrapper.eq(StlSettlement::getPartnerId, partnerId);
        if (StringUtils.hasText(settlementCode)) wrapper.like(StlSettlement::getSettlementCode, settlementCode);
        if (StringUtils.hasText(settlementType)) wrapper.eq(StlSettlement::getSettlementType, settlementType);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(StlSettlement::getRemark, "%" + keyword.trim() + "%"));
        }
        wrapper.orderByDesc(StlSettlement::getCreatedAt);

        Page<StlSettlement> page = stlSettlementMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(m -> toVO(m, resolveNameMaps(page.getRecords())));
    }

    public Map<String, Object> getKpi(Long projectId, Long contractId, Long partnerId,
                                      String settlementCode, String settlementType) {
        Long tenantId = UserContext.getCurrentTenantId();
        LambdaQueryWrapper<StlSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StlSettlement::getTenantId, tenantId);
        if (projectId != null) wrapper.eq(StlSettlement::getProjectId, projectId);
        if (contractId != null) wrapper.eq(StlSettlement::getContractId, contractId);
        if (partnerId != null) wrapper.eq(StlSettlement::getPartnerId, partnerId);
        if (StringUtils.hasText(settlementCode)) wrapper.like(StlSettlement::getSettlementCode, settlementCode);
        if (StringUtils.hasText(settlementType)) wrapper.eq(StlSettlement::getSettlementType, settlementType);

        List<StlSettlement> settlements = stlSettlementMapper.selectList(wrapper);
        // Single-pass accumulation: 5 fields + 2 counters in one loop
        BigDecimal totalContractAmount = BigDecimal.ZERO;
        BigDecimal totalFinalAmount = BigDecimal.ZERO;
        BigDecimal totalChangeAmount = BigDecimal.ZERO;
        BigDecimal totalPaidAmount = BigDecimal.ZERO;
        BigDecimal totalUnpaidAmount = BigDecimal.ZERO;
        long draftCount = 0;
        long finalizedCount = 0;

        for (StlSettlement settlement : settlements) {
            totalContractAmount = totalContractAmount.add(nullToZero(settlement.getContractAmount()));
            totalFinalAmount = totalFinalAmount.add(nullToZero(settlement.getFinalAmount()));
            totalChangeAmount = totalChangeAmount.add(nullToZero(settlement.getChangeAmount()));
            totalPaidAmount = totalPaidAmount.add(nullToZero(settlement.getPaidAmount()));
            totalUnpaidAmount = totalUnpaidAmount.add(nullToZero(settlement.getUnpaidAmount()));
            if ("DRAFT".equals(settlement.getSettlementStatus())) {
                draftCount++;
            } else if ("FINALIZED".equals(settlement.getSettlementStatus())) {
                finalizedCount++;
            }
        }

        return Map.of(
                "totalCount", (long) settlements.size(),
                "totalContractAmount", totalContractAmount.toPlainString(),
                "totalFinalAmount", totalFinalAmount.toPlainString(),
                "totalChangeAmount", totalChangeAmount.toPlainString(),
                "totalPaidAmount", totalPaidAmount.toPlainString(),
                "totalUnpaidAmount", totalUnpaidAmount.toPlainString(),
                "draftCount", draftCount,
                "finalizedCount", finalizedCount);
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

        // P0-01: Prevent duplicate settlements for the same contract
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

        // P0-01: Database-level UNIQUE constraint (tenant_id, contract_id) as safety net
        // against TOCTOU race. The selectCount check above is the fast path;
        // this catch handles the concurrent edge case.
        try {
            stlSettlementMapper.insert(settlement);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("STL_DUPLICATE_SETTLEMENT",
                    "该合同已存在结算单，不允许重复创建");
        }
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

        // 先删明细，再删主记录。
        // 注意：数据库层 stl_settlement_item.settlement_id 需设置 ON DELETE CASCADE 外键约束，
        // 以确保数据一致性。当前应用层显式删除明细作为双重保护。
        // 如果外键未配置 CASCADE，仅依赖应用层删除存在事务回滚时的不一致风险。
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
    // Related data queries (read-only)
    // ================================================================

    /**
     * Query variation orders linked to this settlement via contractId.
     */
    public List<VarOrder> getVariations(Long settlementId) {
        StlSettlement settlement = validateAndGetSettlement(settlementId);
        Long tenantId = UserContext.getCurrentTenantId();
        return varOrderMapper.selectList(new LambdaQueryWrapper<VarOrder>()
                .eq(VarOrder::getTenantId, tenantId)
                .eq(VarOrder::getContractId, settlement.getContractId()));
    }

    /**
     * Query payment records linked to this settlement via contractId.
     */
    public List<PayRecord> getPayments(Long settlementId) {
        StlSettlement settlement = validateAndGetSettlement(settlementId);
        Long tenantId = UserContext.getCurrentTenantId();
        return payRecordMapper.selectList(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, tenantId)
                .eq(PayRecord::getContractId, settlement.getContractId()));
    }

    /**
     * Query cost items linked to this settlement via contractId.
     */
    public List<CostItem> getCosts(Long settlementId) {
        StlSettlement settlement = validateAndGetSettlement(settlementId);
        Long tenantId = UserContext.getCurrentTenantId();
        return costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, tenantId)
                .eq(CostItem::getContractId, settlement.getContractId()));
    }

    /**
     * Query file attachments for this settlement.
     */
    public List<SysFile> getAttachments(Long settlementId) {
        validateAndGetSettlement(settlementId);
        Long tenantId = UserContext.getCurrentTenantId();
        return sysFileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, tenantId)
                .eq(SysFile::getBusinessType, "SETTLEMENT")
                .eq(SysFile::getBusinessId, settlementId));
    }

    /**
     * Query approval records for this settlement's workflow instance.
     */
    public List<SettlementApprovalRecordVO> getApprovalRecords(Long settlementId) {
        validateAndGetSettlement(settlementId);
        Long tenantId = UserContext.getCurrentTenantId();

        LambdaQueryWrapper<WfInstance> instQw = new LambdaQueryWrapper<>();
        instQw.eq(WfInstance::getBusinessType, WorkflowBusinessTypes.SETTLEMENT)
                .eq(WfInstance::getBusinessId, settlementId)
                .eq(WfInstance::getTenantId, tenantId);
        WfInstance instance = wfInstanceMapper.selectOne(instQw);
        if (instance == null) return List.of();

        LambdaQueryWrapper<WfRecord> recQw = new LambdaQueryWrapper<>();
        recQw.eq(WfRecord::getInstanceId, instance.getId())
                .eq(WfRecord::getTenantId, tenantId)
                .orderByAsc(WfRecord::getCreatedAt);
        List<WfRecord> records = wfRecordMapper.selectList(recQw);

        return records.stream().map(r -> {
            SettlementApprovalRecordVO vo = new SettlementApprovalRecordVO();
            vo.setId(r.getId() != null ? r.getId().toString() : null);
            vo.setNodeName(r.getNodeName());
            vo.setOperatorName(r.getOperatorName());
            vo.setActionType(r.getActionType());
            vo.setActionName(r.getActionName());
            vo.setComment(r.getComment());
            vo.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().format(DateTimeUtils.DTF) : null);
            return vo;
        }).toList();
    }

    /**
     * Submit settlement for approval via workflow engine.
     */
    @Transactional
    public void submitForApproval(Long settlementId) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement settlement = stlSettlementMapper.selectById(settlementId);
        if (settlement == null || !Objects.equals(settlement.getTenantId(), tenantId)) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        if (!"DRAFT".equals(settlement.getApprovalStatus())) {
            throw new BusinessException("STL_ALREADY_SUBMITTED", "结算单已提交审批，不可重复提交");
        }

        // Recompute amount snapshot before approval — ensures the snapshot
        // reflects the latest contract data at submission time, not just at creation time.
        CtContract contract = ctContractMapper.selectById(settlement.getContractId());
        autoFillAmounts(settlement, contract);

        // Persist updated amounts + status
        stlSettlementMapper.update(null, new LambdaUpdateWrapper<StlSettlement>()
                .eq(StlSettlement::getId, settlementId)
                .set(StlSettlement::getApprovalStatus, "APPROVING"));

        // Submit to workflow engine
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

    // ---- Internal helper ----

    private StlSettlement validateAndGetSettlement(Long settlementId) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement settlement = stlSettlementMapper.selectById(settlementId);
        if (settlement == null || !Objects.equals(settlement.getTenantId(), tenantId)) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        return settlement;
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

        // warrantyAmount = finalAmount × default warranty rate
        BigDecimal warrantyRate = DEFAULT_WARRANTY_RATE;
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

    // ---- Source data query ----

    public SettlementSourcesVO getSources(Long settlementId) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement settlement = stlSettlementMapper.selectById(settlementId);
        if (settlement == null || !Objects.equals(settlement.getTenantId(), tenantId)) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        Long contractId = settlement.getContractId();

        // Query confirmed var orders
        List<VarOrder> varOrders = varOrderMapper.selectList(
            new LambdaQueryWrapper<VarOrder>()
                .eq(VarOrder::getTenantId, tenantId)
                .eq(VarOrder::getContractId, contractId)
                .eq(VarOrder::getDirection, "COST")
                .eq(VarOrder::getOwnerConfirmFlag, 1));

        // Query approved sub measures
        List<SubMeasure> subMeasures = subMeasureMapper.selectList(
            new LambdaQueryWrapper<SubMeasure>()
                .eq(SubMeasure::getTenantId, tenantId)
                .eq(SubMeasure::getContractId, contractId)
                .eq(SubMeasure::getApprovalStatus, "APPROVED"));

        // Query success pay records
        List<PayRecord> payRecords = payRecordMapper.selectList(
            new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, tenantId)
                .eq(PayRecord::getContractId, contractId)
                .eq(PayRecord::getPayStatus, "SUCCESS"));

        // Map to VOs
        SettlementSourcesVO vo = new SettlementSourcesVO();
        vo.setVarOrders(varOrders.stream().map(v -> {
            SettlementSourcesVO.VarOrderVO vvo = new SettlementSourcesVO.VarOrderVO();
            vvo.setId(v.getId());
            vvo.setVarCode(v.getVarCode());
            vvo.setVarName(v.getVarName());
            vvo.setVarType(v.getVarType());
            vvo.setConfirmedAmount(v.getConfirmedAmount());
            vvo.setApprovalStatus(v.getApprovalStatus());
            return vvo;
        }).collect(Collectors.toList()));

        vo.setSubMeasures(subMeasures.stream().map(s -> {
            SettlementSourcesVO.SubMeasureVO svo = new SettlementSourcesVO.SubMeasureVO();
            svo.setId(s.getId());
            svo.setMeasureCode(s.getMeasureCode());
            svo.setMeasurePeriod(s.getMeasurePeriod());
            svo.setApprovedAmount(s.getApprovedAmount());
            svo.setApprovalStatus(s.getApprovalStatus());
            return svo;
        }).collect(Collectors.toList()));

        vo.setPayRecords(payRecords.stream().map(p -> {
            SettlementSourcesVO.PayRecordVO pvo = new SettlementSourcesVO.PayRecordVO();
            pvo.setId(p.getId());
            pvo.setPayAmount(p.getPayAmount());
            pvo.setPayDate(p.getPayDate() != null ? p.getPayDate().toString() : null);
            pvo.setPayMethod(p.getPayMethod());
            pvo.setVoucherNo(p.getVoucherNo());
            pvo.setPayStatus(p.getPayStatus());
            return pvo;
        }).collect(Collectors.toList()));

        return vo;
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

        // warrantyAmount = finalAmount × default warranty rate
        BigDecimal warrantyRate = DEFAULT_WARRANTY_RATE;
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
        vo.setFinalizedAt(m.getFinalizedAt() != null ? m.getFinalizedAt().format(DateTimeUtils.DTF) : null);
        vo.setProjectName(maps.projectNames().get(m.getProjectId()));
        vo.setContractName(maps.contractNames().get(m.getContractId()));
        vo.setPartnerName(maps.partnerNames().get(m.getPartnerId()));
        vo.setCreatedBy(m.getCreatedBy() != null ? m.getCreatedBy().toString() : null);
        vo.setCreatedAt(m.getCreatedAt() != null ? m.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt().format(DateTimeUtils.DTF) : null);
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
        vo.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(item.getRemark());
        return vo;
    }

    // ---- Internal helper record ----

    private record NameMaps(Map<Long, String> projectNames,
                            Map<Long, String> contractNames,
                            Map<Long, String> partnerNames) {
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
