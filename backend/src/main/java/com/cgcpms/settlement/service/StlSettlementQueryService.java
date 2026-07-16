package com.cgcpms.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.settlement.constant.SettlementStatusConstants;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.StlSettlementItem;
import com.cgcpms.settlement.mapper.StlSettlementItemMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.vo.SettlementApprovalRecordVO;
import com.cgcpms.settlement.vo.SettlementAmountBaselineVO;
import com.cgcpms.settlement.vo.SettlementAttachmentVO;
import com.cgcpms.settlement.vo.SettlementCostItemVO;
import com.cgcpms.settlement.vo.SettlementPaymentItemVO;
import com.cgcpms.settlement.vo.SettlementSourcesVO;
import com.cgcpms.settlement.vo.StlSettlementItemVO;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.variation.service.VarOrderService;
import com.cgcpms.variation.vo.VarOrderVO;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfRecord;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.cgcpms.settlement.constant.SettlementStatusConstants.SETTLEMENT_DRAFT;
import static com.cgcpms.settlement.constant.SettlementStatusConstants.SETTLEMENT_FINALIZED;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 结算只读查询服务 — 所有查询、汇总、关联数据读取。
 * 不包含任何写操作（INSERT/UPDATE/DELETE）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StlSettlementQueryService {

    private final StlSettlementMapper stlSettlementMapper;
    private final StlSettlementItemMapper stlSettlementItemMapper;
    private final CtContractMapper ctContractMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final VarOrderMapper varOrderMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final PayApplicationMapper payApplicationMapper;
    private final PayRecordMapper payRecordMapper;
    private final CostItemMapper costItemMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final SysFileMapper sysFileMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfRecordMapper wfRecordMapper;
    private final StlSettlementAssembler assembler;
    private final VarOrderService varOrderService;

    // ================================================================
    // Page / KPI / Detail
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
        return page.convert(m -> assembler.toVO(m, resolveNameMaps(page.getRecords())));
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
            totalContractAmount = totalContractAmount.add(StlSettlementAssembler.nullToZero(settlement.getContractAmount()));
            totalFinalAmount = totalFinalAmount.add(StlSettlementAssembler.nullToZero(settlement.getFinalAmount()));
            totalChangeAmount = totalChangeAmount.add(StlSettlementAssembler.nullToZero(settlement.getChangeAmount()));
            totalPaidAmount = totalPaidAmount.add(StlSettlementAssembler.nullToZero(settlement.getPaidAmount()));
            totalUnpaidAmount = totalUnpaidAmount.add(StlSettlementAssembler.nullToZero(settlement.getUnpaidAmount()));
            if (SETTLEMENT_DRAFT.equals(settlement.getSettlementStatus())) {
                draftCount++;
            } else if (SETTLEMENT_FINALIZED.equals(settlement.getSettlementStatus())) {
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

        StlSettlementVO vo = assembler.toVO(settlement, resolveNameMaps(List.of(settlement)));

        // Load items
        List<StlSettlementItem> items = stlSettlementItemMapper.selectList(
                new LambdaQueryWrapper<StlSettlementItem>()
                        .eq(StlSettlementItem::getTenantId, tenantId)
                        .eq(StlSettlementItem::getSettlementId, id));
        vo.setItems(items.stream().map(assembler::toItemVO).collect(Collectors.toList()));

        return vo;
    }

    // ================================================================
    // Amount computation (read-only)
    // ================================================================

    /**
     * 纯只读汇总计算：基于合同 + 变更 + 计量 + 付款记录计算结算金额。
     */
    public StlSettlementVO computeSettlementAmount(Long contractId) {
        Long tenantId = UserContext.getCurrentTenantId();

        CtContract contract = ctContractMapper.selectById(contractId);
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)) {
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        }

        SettlementAmountSnapshot snapshot = SettlementAmountPolicy.calculate(
                contract.getCurrentAmount(),
                sumVarOrderConfirmed(tenantId, contractId),
                sumSubMeasureApproved(tenantId, contractId),
                BigDecimal.ZERO,
                sumPaidAmount(tenantId, contractId));

        StlSettlementVO vo = new StlSettlementVO();
        vo.setContractAmount(snapshot.effectiveContractAmount().toPlainString());
        vo.setChangeAmount(snapshot.confirmedVariationAmount().toPlainString());
        vo.setMeasuredAmount(snapshot.approvedMeasuredAmount().toPlainString());
        vo.setDeductionAmount(snapshot.deductionAmount().toPlainString());
        vo.setPaidAmount(snapshot.paidAmount().toPlainString());
        vo.setFinalAmount(snapshot.finalAmount().toPlainString());
        vo.setWarrantyAmount(snapshot.warrantyAmount().toPlainString());
        vo.setUnpaidAmount(snapshot.unpaidAmount().toPlainString());
        vo.setAmountFormulaVersion(snapshot.formulaVersion());
        vo.setContractName(contract.getContractName());
        vo.setProjectId(contract.getProjectId() != null ? contract.getProjectId().toString() : null);
        return vo;
    }

    /**
     * 逐条预览历史结算快照与当前权威来源重算值的差异，不修改任何数据。
     */
    public IPage<SettlementAmountBaselineVO> previewAmountBaseline(long pageNo, long pageSize) {
        Long tenantId = UserContext.getCurrentTenantId();
        long safePageNo = Math.max(1, pageNo);
        long safePageSize = Math.min(100, Math.max(1, pageSize));
        Page<StlSettlement> page = stlSettlementMapper.selectPage(
                new Page<>(safePageNo, safePageSize),
                new LambdaQueryWrapper<StlSettlement>()
                        .eq(StlSettlement::getTenantId, tenantId)
                        .orderByAsc(StlSettlement::getCreatedAt));
        return page.convert(settlement -> toAmountBaseline(settlement, tenantId));
    }

    private SettlementAmountBaselineVO toAmountBaseline(StlSettlement settlement, Long tenantId) {
        SettlementAmountBaselineVO vo = new SettlementAmountBaselineVO();
        vo.setSettlementId(String.valueOf(settlement.getId()));
        vo.setSettlementCode(settlement.getSettlementCode());
        vo.setProjectId(settlement.getProjectId() != null ? settlement.getProjectId().toString() : null);
        vo.setContractId(settlement.getContractId() != null ? settlement.getContractId().toString() : null);
        vo.setStoredFormulaVersion(settlement.getAmountFormulaVersion());
        vo.setTargetFormulaVersion(SettlementAmountPolicy.FORMULA_VERSION);

        CtContract contract = settlement.getContractId() == null
                ? null
                : ctContractMapper.selectById(settlement.getContractId());
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)) {
            vo.setAmountConsistent(false);
            vo.setFormulaVersionCurrent(false);
            vo.setRecommendedAction("MISSING_CONTRACT");
            return vo;
        }

        SettlementAmountSnapshot recalculated = SettlementAmountPolicy.calculate(
                contract.getCurrentAmount(),
                sumVarOrderConfirmed(tenantId, contract.getId()),
                sumSubMeasureApproved(tenantId, contract.getId()),
                settlement.getDeductionAmount(),
                sumPaidAmount(tenantId, contract.getId()));

        vo.setStoredContractAmount(plain(settlement.getContractAmount()));
        vo.setCurrentEffectiveContractAmount(recalculated.effectiveContractAmount().toPlainString());
        vo.setStoredChangeAmount(plain(settlement.getChangeAmount()));
        vo.setCurrentConfirmedVariationAmount(recalculated.confirmedVariationAmount().toPlainString());
        vo.setStoredMeasuredAmount(plain(settlement.getMeasuredAmount()));
        vo.setCurrentApprovedMeasuredAmount(recalculated.approvedMeasuredAmount().toPlainString());
        vo.setDeductionAmount(recalculated.deductionAmount().toPlainString());
        vo.setStoredPaidAmount(plain(settlement.getPaidAmount()));
        vo.setCurrentPaidAmount(recalculated.paidAmount().toPlainString());
        vo.setStoredFinalAmount(plain(settlement.getFinalAmount()));
        vo.setRecalculatedFinalAmount(recalculated.finalAmount().toPlainString());
        vo.setFinalAmountDelta(recalculated.finalAmount()
                .subtract(SettlementAmountPolicy.money(settlement.getFinalAmount()))
                .toPlainString());
        vo.setStoredWarrantyAmount(plain(settlement.getWarrantyAmount()));
        vo.setRecalculatedWarrantyAmount(recalculated.warrantyAmount().toPlainString());
        vo.setStoredUnpaidAmount(plain(settlement.getUnpaidAmount()));
        vo.setRecalculatedUnpaidAmount(recalculated.unpaidAmount().toPlainString());

        boolean amountConsistent = same(settlement.getContractAmount(), recalculated.effectiveContractAmount())
                && same(settlement.getChangeAmount(), recalculated.confirmedVariationAmount())
                && same(settlement.getMeasuredAmount(), recalculated.approvedMeasuredAmount())
                && same(settlement.getDeductionAmount(), recalculated.deductionAmount())
                && same(settlement.getPaidAmount(), recalculated.paidAmount())
                && same(settlement.getFinalAmount(), recalculated.finalAmount())
                && same(settlement.getWarrantyAmount(), recalculated.warrantyAmount())
                && same(settlement.getUnpaidAmount(), recalculated.unpaidAmount());
        boolean formulaVersionCurrent = SettlementAmountPolicy.FORMULA_VERSION
                .equals(settlement.getAmountFormulaVersion());
        vo.setAmountConsistent(amountConsistent);
        vo.setFormulaVersionCurrent(formulaVersionCurrent);
        vo.setRecommendedAction(!amountConsistent
                ? "REVIEW_AMOUNT_DRIFT"
                : formulaVersionCurrent ? "NO_CHANGE" : "BACKFILL_FORMULA_VERSION");
        return vo;
    }

    private static boolean same(BigDecimal stored, BigDecimal expected) {
        return SettlementAmountPolicy.money(stored).compareTo(expected) == 0;
    }

    private static String plain(BigDecimal value) {
        return SettlementAmountPolicy.money(value).toPlainString();
    }

    // ================================================================
    // Source data query
    // ================================================================

    public SettlementSourcesVO getSources(Long settlementId) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement settlement = stlSettlementMapper.selectById(settlementId);
        if (settlement == null || !Objects.equals(settlement.getTenantId(), tenantId)) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        Long contractId = settlement.getContractId();

        List<VarOrder> varOrders = varOrderMapper.selectList(
            new LambdaQueryWrapper<VarOrder>()
                .eq(VarOrder::getTenantId, tenantId)
                .eq(VarOrder::getContractId, contractId)
                .eq(VarOrder::getDirection, "COST")
                .eq(VarOrder::getOwnerConfirmFlag, 1));

        List<SubMeasure> subMeasures = subMeasureMapper.selectList(
            new LambdaQueryWrapper<SubMeasure>()
                .eq(SubMeasure::getTenantId, tenantId)
                .eq(SubMeasure::getContractId, contractId)
                .eq(SubMeasure::getApprovalStatus, "APPROVED"));

        List<PayRecord> payRecords = payRecordMapper.selectList(
            new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, tenantId)
                .eq(PayRecord::getContractId, contractId)
                .eq(PayRecord::getPayStatus, "SUCCESS"));

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

    // ================================================================
    // Related data queries (read-only)
    // ================================================================

    public List<VarOrderVO> getVariations(Long settlementId) {
        StlSettlement settlement = validateAndGetSettlement(settlementId);
        Long tenantId = UserContext.getCurrentTenantId();
        List<VarOrder> variations = varOrderMapper.selectList(new LambdaQueryWrapper<VarOrder>()
                .eq(VarOrder::getTenantId, tenantId)
                .eq(VarOrder::getContractId, settlement.getContractId()));
        return varOrderService.toVOList(variations);
    }

    public List<SettlementPaymentItemVO> getPayments(Long settlementId) {
        StlSettlement settlement = validateAndGetSettlement(settlementId);
        Long tenantId = UserContext.getCurrentTenantId();
        List<PayRecord> payRecords = payRecordMapper.selectList(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, tenantId)
                .eq(PayRecord::getContractId, settlement.getContractId()));
        if (payRecords.isEmpty()) {
            return List.of();
        }

        Map<Long, PayApplication> applicationsById = payApplicationMapper.selectByIds(
                        payRecords.stream()
                                .map(PayRecord::getPayApplicationId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet()))
                .stream()
                .filter(application -> Objects.equals(application.getTenantId(), tenantId))
                .collect(Collectors.toMap(PayApplication::getId, application -> application));

        return payRecords.stream()
                .map(record -> toSettlementPaymentItemVO(record, applicationsById.get(record.getPayApplicationId())))
                .toList();
    }

    public List<SettlementCostItemVO> getCosts(Long settlementId) {
        StlSettlement settlement = validateAndGetSettlement(settlementId);
        Long tenantId = UserContext.getCurrentTenantId();
        List<CostItem> costItems = costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, tenantId)
                .eq(CostItem::getContractId, settlement.getContractId()));
        Map<Long, String> subjectNames = resolveCostSubjectNames(costItems);
        return costItems.stream()
                .map(item -> toSettlementCostItemVO(item, subjectNames))
                .toList();
    }

    public List<SettlementAttachmentVO> getAttachments(Long settlementId) {
        validateAndGetSettlement(settlementId);
        Long tenantId = UserContext.getCurrentTenantId();
        return sysFileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, tenantId)
                .eq(SysFile::getBusinessType, "SETTLEMENT")
                .eq(SysFile::getBusinessId, settlementId))
                .stream()
                .map(this::toSettlementAttachmentVO)
                .toList();
    }

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

    // ================================================================
    // Shared helpers (package-private — used by WriteService)
    // ================================================================

    /**
     * 验证结算单存在且属于当前租户。
     */
    StlSettlement validateAndGetSettlement(Long settlementId) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement settlement = stlSettlementMapper.selectById(settlementId);
        if (settlement == null || !Objects.equals(settlement.getTenantId(), tenantId)) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        return settlement;
    }

    BigDecimal sumVarOrderConfirmed(Long tenantId, Long contractId) {
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

    BigDecimal sumSubMeasureApproved(Long tenantId, Long contractId) {
        LambdaQueryWrapper<SubMeasure> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubMeasure::getTenantId, tenantId);
        wrapper.eq(SubMeasure::getContractId, contractId);
        wrapper.eq(SubMeasure::getApprovalStatus, "APPROVED");
        List<SubMeasure> measures = subMeasureMapper.selectList(wrapper);
        return measures.stream()
                .map(m -> m.getApprovedAmount() != null ? m.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    BigDecimal sumPaidAmount(Long tenantId, Long contractId) {
        LambdaQueryWrapper<PayRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayRecord::getTenantId, tenantId);
        wrapper.eq(PayRecord::getContractId, contractId);
        wrapper.eq(PayRecord::getPayStatus, "SUCCESS");
        List<PayRecord> records = payRecordMapper.selectList(wrapper);
        return records.stream()
                .map(r -> r.getPayAmount() != null ? r.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ================================================================
    // Name resolution (private)
    // ================================================================

    private StlSettlementAssembler.NameMaps resolveNameMaps(List<StlSettlement> records) {
        Set<Long> projectIds = records.stream().map(StlSettlement::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> contractIds = records.stream().map(StlSettlement::getContractId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> partnerIds = records.stream().map(StlSettlement::getPartnerId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> projectNames = projectIds.isEmpty() ? Collections.emptyMap()
                : pmProjectMapper.selectByIds(projectIds).stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> contractNames = contractIds.isEmpty() ? Collections.emptyMap()
                : ctContractMapper.selectByIds(contractIds).stream()
                .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Collections.emptyMap()
                : mdPartnerMapper.selectByIds(partnerIds).stream()
                .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));

        return new StlSettlementAssembler.NameMaps(projectNames, contractNames, partnerNames);
    }

    private Map<Long, String> resolveCostSubjectNames(List<CostItem> items) {
        Set<Long> subjectIds = items.stream()
                .map(CostItem::getCostSubjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        return costSubjectMapper.selectByIds(subjectIds).stream()
                .collect(Collectors.toMap(CostSubject::getId, CostSubject::getSubjectName, (a, b) -> a));
    }

    private SettlementCostItemVO toSettlementCostItemVO(CostItem item, Map<Long, String> subjectNames) {
        SettlementCostItemVO vo = new SettlementCostItemVO();
        vo.setId(item.getId() != null ? item.getId().toString() : null);
        vo.setCostSubjectId(item.getCostSubjectId() != null ? item.getCostSubjectId().toString() : null);
        vo.setCostSubjectName(item.getCostSubjectId() == null ? null : subjectNames.get(item.getCostSubjectId()));
        vo.setCostType(item.getCostType());
        vo.setSourceType(item.getSourceType());
        vo.setSourceId(item.getSourceId() != null ? item.getSourceId().toString() : null);
        vo.setSourceItemId(item.getSourceItemId() != null ? item.getSourceItemId().toString() : null);
        vo.setAmount(item.getAmount() != null ? item.getAmount().toPlainString() : null);
        vo.setTaxAmount(item.getTaxAmount() != null ? item.getTaxAmount().toPlainString() : null);
        vo.setAmountWithoutTax(item.getAmountWithoutTax() != null ? item.getAmountWithoutTax().toPlainString() : null);
        vo.setCostDate(item.getCostDate() != null ? DateTimeUtils.DATE_FMT.format(item.getCostDate()) : null);
        vo.setCostStatus(item.getCostStatus());
        return vo;
    }

    private SettlementAttachmentVO toSettlementAttachmentVO(SysFile file) {
        SettlementAttachmentVO vo = new SettlementAttachmentVO();
        vo.setId(file.getId() != null ? file.getId().toString() : null);
        vo.setOriginalName(file.getOriginalName());
        vo.setFileSize(file.getFileSize());
        vo.setFileType(file.getContentType());
        vo.setUploadedBy(file.getCreatedBy() != null ? file.getCreatedBy().toString() : null);
        vo.setUploadedAt(file.getCreatedAt() != null ? file.getCreatedAt().format(DateTimeUtils.DTF) : null);
        return vo;
    }

    private SettlementPaymentItemVO toSettlementPaymentItemVO(PayRecord record, PayApplication application) {
        SettlementPaymentItemVO vo = new SettlementPaymentItemVO();
        vo.setId(record.getId() != null ? record.getId().toString() : null);
        vo.setApplicationId(record.getPayApplicationId() != null ? record.getPayApplicationId().toString() : null);
        vo.setApplyCode(application != null ? application.getApplyCode() : null);
        vo.setPayType(application != null ? application.getPayType() : null);
        vo.setApplyAmount(application != null && application.getApplyAmount() != null
                ? application.getApplyAmount().toPlainString() : null);
        vo.setApprovedAmount(application != null && application.getApprovedAmount() != null
                ? application.getApprovedAmount().toPlainString() : null);
        vo.setActualPayAmount(record.getPayAmount() != null ? record.getPayAmount().toPlainString() : null);
        vo.setPayStatus(normalizeSettlementPayStatus(application != null ? application.getPayStatus() : null));
        vo.setPayDate(record.getPayDate() != null ? record.getPayDate().toString() : null);
        vo.setVoucherNo(record.getVoucherNo());
        vo.setCreatedAt(record.getCreatedAt() != null ? record.getCreatedAt().format(DateTimeUtils.DTF) : null);
        return vo;
    }

    private String normalizeSettlementPayStatus(String payStatus) {
        if (payStatus == null) {
            return null;
        }
        return switch (payStatus) {
            case "PAID" -> "PAID";
            case "PARTIALLY_PAID" -> "PARTIAL";
            case "APPROVED" -> "UNPAID";
            default -> payStatus;
        };
    }
}
