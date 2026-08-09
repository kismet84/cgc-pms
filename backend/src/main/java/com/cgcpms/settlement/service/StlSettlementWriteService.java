package com.cgcpms.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.file.service.FileLifecycleGateway;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.settlement.constant.SettlementStatusConstants;
import com.cgcpms.settlement.entity.SettlementSubMeasure;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.StlSettlementItem;
import com.cgcpms.settlement.mapper.StlSettlementItemMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.mapper.SettlementSubMeasureMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.cgcpms.settlement.constant.SettlementStatusConstants.APPROVAL_APPROVING;
import static com.cgcpms.settlement.constant.SettlementStatusConstants.APPROVAL_DRAFT;
import static com.cgcpms.settlement.constant.SettlementStatusConstants.SETTLEMENT_DRAFT;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 结算写操作服务 — 创建/更新/删除 + 明细管理 + 审批提交。
 * 依赖 StlSettlementQueryService 做只读汇总计算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StlSettlementWriteService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;

    private final StlSettlementMapper stlSettlementMapper;
    private final StlSettlementItemMapper stlSettlementItemMapper;
    private final CtContractMapper ctContractMapper;
    private final WorkflowEngine workflowEngine;
    private final StlSettlementQueryService queryService;
    private final SettlementSubMeasureMapper settlementSubMeasureMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final SubMeasureItemMapper subMeasureItemMapper;
    private final CtContractItemMapper ctContractItemMapper;
    private final FileLifecycleGateway fileLifecycleGateway;
    private final SysFileMapper fileMapper;
    private final PmProjectMapper projectMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final WfInstanceMapper wfInstanceMapper;
    private final VarOrderMapper varOrderMapper;
    private final PayRecordMapper payRecordMapper;
    private final CodeGenerationService codeGenerationService;

    // ================================================================
    // Create
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Long create(StlSettlement settlement) {
        Long tenantId = UserContext.getCurrentTenantId();
        settlement.setId(null);
        settlement.setTenantId(tenantId);

        CtContract contract = validateAndGetContract(settlement.getContractId(), tenantId, settlement.getProjectId());
        projectAccessChecker.checkAccess(contract.getProjectId(), "创建结算单");
        settlement.setProjectId(contract.getProjectId());
        settlement.setPartnerId(contract.getPartyBId());

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
        // Default statuses
        settlement.setApprovalStatus(APPROVAL_DRAFT);
        settlement.setSettlementStatus(SETTLEMENT_DRAFT);
        settlement.setSettlementType("FINAL");
        settlement.setFinalizedAt(null);

        // Auto-compute amounts
        autoFillAmounts(settlement, contract);

        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            settlement.setSettlementCode(codeGenerationService.nextCode(
                    stlSettlementMapper, StlSettlement::getSettlementCode,
                    "STL-", tenantId, true, attempt));
            try {
                stlSettlementMapper.insert(settlement);
                return settlement.getId();
            } catch (DuplicateKeyException e) {
                Long duplicateContractCount = stlSettlementMapper.selectCount(
                        new LambdaQueryWrapper<StlSettlement>()
                                .eq(StlSettlement::getTenantId, tenantId)
                                .eq(StlSettlement::getContractId, settlement.getContractId()));
                if (duplicateContractCount > 0) {
                    throw new BusinessException("STL_DUPLICATE_SETTLEMENT",
                            "该合同已存在结算单，不允许重复创建");
                }
                log.warn("结算单编号冲突，重试生成 settlementCode={}", settlement.getSettlementCode());
            }
        }
        throw new BusinessException("STL_CODE_CONFLICT", "结算单编号生成冲突，请重试");
    }

    // ================================================================
    // Update / Delete
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void update(StlSettlement settlement) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement existing = stlSettlementMapper.selectByIdForUpdate(settlement.getId(), tenantId);
        if (existing == null) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        projectAccessChecker.checkAccess(existing.getProjectId(), "编辑结算单");
        if (!Set.of(APPROVAL_DRAFT, SettlementStatusConstants.APPROVAL_REJECTED)
                .contains(existing.getApprovalStatus())) {
            throw new BusinessException("STL_SETTLEMENT_IN_APPROVAL", "结算单审批中或已审批，不可编辑");
        }

        Long contractId = settlement.getContractId() == null
                ? existing.getContractId() : settlement.getContractId();
        CtContract contract = validateAndGetContract(contractId, tenantId, existing.getProjectId());
        boolean contractChanged = !Objects.equals(contract.getId(), existing.getContractId());
        existing.setContractId(contract.getId());
        existing.setProjectId(contract.getProjectId());
        existing.setPartnerId(contract.getPartyBId());
        existing.setSettlementType("FINAL");
        existing.setDeductionAmount(settlement.getDeductionAmount());
        existing.setRemark(settlement.getRemark());
        autoFillAmounts(existing, contract);
        if (contractChanged) {
            stlSettlementItemMapper.delete(new LambdaQueryWrapper<StlSettlementItem>()
                    .eq(StlSettlementItem::getTenantId, tenantId)
                    .eq(StlSettlementItem::getSettlementId, existing.getId()));
            settlementSubMeasureMapper.delete(new LambdaQueryWrapper<SettlementSubMeasure>()
                    .eq(SettlementSubMeasure::getTenantId, tenantId)
                    .eq(SettlementSubMeasure::getSettlementId, existing.getId()));
        }
        if (stlSettlementMapper.updateById(existing) != 1) {
            throw new BusinessException("STL_SETTLEMENT_CONCURRENT_MODIFICATION", "结算单已被修改，请刷新后重试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement existing = stlSettlementMapper.selectByIdForUpdate(id, tenantId);
        if (existing == null) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        projectAccessChecker.checkAccess(existing.getProjectId(), "删除结算单");
        if (!Set.of(APPROVAL_DRAFT, SettlementStatusConstants.APPROVAL_REJECTED)
                .contains(existing.getApprovalStatus())) {
            throw new BusinessException("STL_SETTLEMENT_IN_APPROVAL", "结算单审批中或已审批，不可删除");
        }

        fileLifecycleGateway.deleteAllForBusinessCascade("SETTLEMENT", id);
        stlSettlementItemMapper.delete(new LambdaQueryWrapper<StlSettlementItem>()
                .eq(StlSettlementItem::getTenantId, tenantId)
                .eq(StlSettlementItem::getSettlementId, id));
        settlementSubMeasureMapper.delete(new LambdaQueryWrapper<SettlementSubMeasure>()
                .eq(SettlementSubMeasure::getTenantId, tenantId)
                .eq(SettlementSubMeasure::getSettlementId, id));
        if (stlSettlementMapper.update(null, new LambdaUpdateWrapper<StlSettlement>()
                .set(StlSettlement::getContractId, null)
                .set(StlSettlement::getSettlementCode, "DELETED-" + id)
                .eq(StlSettlement::getId, id)
                .eq(StlSettlement::getTenantId, tenantId)
                .eq(StlSettlement::getDeletedFlag, 0)) != 1) {
            throw new BusinessException("STL_SETTLEMENT_CONCURRENT_MODIFICATION", "结算单已被修改，请刷新后重试");
        }
        if (stlSettlementMapper.deleteById(id) != 1) {
            throw new BusinessException("STL_SETTLEMENT_CONCURRENT_MODIFICATION", "结算单已被修改，请刷新后重试");
        }
    }

    // ================================================================
    // Items management
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long settlementId, List<StlSettlementItem> items) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement settlement = stlSettlementMapper.selectByIdForUpdate(settlementId, tenantId);
        if (settlement == null) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        projectAccessChecker.checkAccess(settlement.getProjectId(), "编辑结算明细");
        if (!Set.of(APPROVAL_DRAFT, SettlementStatusConstants.APPROVAL_REJECTED)
                .contains(settlement.getApprovalStatus())) {
            throw new BusinessException("STL_SETTLEMENT_IN_APPROVAL", "结算单审批中或已审批，不可编辑");
        }

        List<StlSettlementItem> requests = items == null ? List.of() : new ArrayList<>(items);
        if (requests.size() > 200) {
            throw new BusinessException("STL_SETTLEMENT_ITEMS_LIMIT", "结算明细不能超过200条");
        }
        Map<Long, ContractItemSnapshot> availableSources = approvedContractItemSnapshots(settlement).stream()
                .collect(java.util.stream.Collectors.toMap(
                        snapshot -> snapshot.contractItem().getId(),
                        snapshot -> snapshot,
                        (left, right) -> left,
                        LinkedHashMap::new));
        requests.sort(Comparator.comparing(StlSettlementItem::getSourceId,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        Set<Long> sourceIds = new HashSet<>();
        List<StlSettlementItem> normalizedItems = new ArrayList<>(requests.size());
        for (StlSettlementItem request : requests) {
            if (!"CT_CONTRACT".equals(request.getSourceType()) || request.getSourceId() == null
                    || !sourceIds.add(request.getSourceId())) {
                throw new BusinessException("STL_SETTLEMENT_SOURCE_INVALID",
                        "结算明细必须引用唯一且存在已审批计量的合同清单");
            }
            ContractItemSnapshot source = availableSources.get(request.getSourceId());
            if (source == null) {
                throw new BusinessException("STL_SETTLEMENT_SOURCE_SCOPE_INVALID",
                        "合同清单不存在、无已审批计量或不属于当前结算范围");
            }
            StlSettlementItem normalized = toSettlementItem(settlementId, source, tenantId);
            normalized.setRemark(request.getRemark());
            normalizedItems.add(normalized);
        }

        stlSettlementItemMapper.delete(new LambdaQueryWrapper<StlSettlementItem>()
                .eq(StlSettlementItem::getTenantId, tenantId)
                .eq(StlSettlementItem::getSettlementId, settlementId));

        for (StlSettlementItem item : normalizedItems) {
            stlSettlementItemMapper.insert(item);
        }
    }

    // ================================================================
    // Workflow
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long settlementId) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement settlement = stlSettlementMapper.selectByIdForUpdate(settlementId, tenantId);
        if (settlement == null) {
            throw new BusinessException("STL_SETTLEMENT_NOT_FOUND", "结算单不存在");
        }
        if (!Set.of(APPROVAL_DRAFT, SettlementStatusConstants.APPROVAL_REJECTED)
                .contains(settlement.getApprovalStatus())) {
            throw new BusinessException("STL_ALREADY_SUBMITTED", "结算单已提交审批，不可重复提交");
        }

        LockedSettlementSources sources = lockSettlementSources(settlement);
        validateSettlementIntegrity(settlement, sources.contract(), sources.amounts());
        snapshotApprovedMeasures(settlement, sources.measures());
        applyAmountSnapshot(settlement, sources.amounts());

        int changed = stlSettlementMapper.update(null, new LambdaUpdateWrapper<StlSettlement>()
                .eq(StlSettlement::getId, settlementId)
                .eq(StlSettlement::getTenantId, tenantId)
                .in(StlSettlement::getApprovalStatus,
                        APPROVAL_DRAFT, SettlementStatusConstants.APPROVAL_REJECTED)
                .set(StlSettlement::getContractAmount, settlement.getContractAmount())
                .set(StlSettlement::getChangeAmount, settlement.getChangeAmount())
                .set(StlSettlement::getMeasuredAmount, settlement.getMeasuredAmount())
                .set(StlSettlement::getDeductionAmount, settlement.getDeductionAmount())
                .set(StlSettlement::getPaidAmount, settlement.getPaidAmount())
                .set(StlSettlement::getFinalAmount, settlement.getFinalAmount())
                .set(StlSettlement::getWarrantyAmount, settlement.getWarrantyAmount())
                .set(StlSettlement::getUnpaidAmount, settlement.getUnpaidAmount())
                .set(StlSettlement::getAmountFormulaVersion, settlement.getAmountFormulaVersion())
                .set(StlSettlement::getApprovalStatus, APPROVAL_APPROVING));
        if (changed != 1) {
            throw new BusinessException("STL_SETTLEMENT_CONCURRENT_MODIFICATION", "结算单已被修改，请刷新后重试");
        }

        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        WfInstance existingInstance = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getTenantId, tenantId)
                .eq(WfInstance::getBusinessType, WorkflowBusinessTypes.SETTLEMENT)
                .eq(WfInstance::getBusinessId, settlementId)
                .orderByDesc(WfInstance::getCreatedAt)
                .last("LIMIT 1")); // SQL-SAFETY: fixed-sql-fragment
        if (existingInstance != null) {
            if (WorkflowConstants.INSTANCE_REJECTED.equals(existingInstance.getInstanceStatus())
                    || WorkflowConstants.INSTANCE_WITHDRAWN.equals(existingInstance.getInstanceStatus())) {
                workflowEngine.resubmit(existingInstance.getId(), userId, username);
                return;
            }
            throw new BusinessException("WORKFLOW_INSTANCE_EXISTS", "该结算已提交审批，请勿重复提交");
        }
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
        validateSettlementContract(contract);
        return contract;
    }

    private void validateSettlementIntegrity(
            StlSettlement settlement, CtContract contract, SettlementAmountSnapshot snapshot) {
        Long tenantId = settlement.getTenantId();
        if (settlement.getProjectId() == null || settlement.getContractId() == null
                || settlement.getPartnerId() == null) {
            throw new BusinessException("SETTLEMENT_CONTEXT_REQUIRED", "分包终期结算必须绑定项目、合同和分包商");
        }
        projectAccessChecker.checkAccess(settlement.getProjectId(), "提交分包终期结算");
        PmProject project = projectMapper.selectById(settlement.getProjectId());
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)
                || !ProjectStatusConstants.ACTIVE.equals(project.getStatus())) {
            throw new BusinessException("SETTLEMENT_PROJECT_NOT_ACTIVE", "只有进行中的本租户项目可以提交分包结算");
        }
        validateSettlementContract(contract);
        if (!Objects.equals(contract.getPartyBId(), settlement.getPartnerId())) {
            throw new BusinessException("SETTLEMENT_PARTNER_MISMATCH", "结算分包商必须等于分包合同乙方");
        }
        if (!"FINAL".equalsIgnoreCase(settlement.getSettlementType())) {
            throw new BusinessException("SETTLEMENT_TYPE_INVALID", "本闭环结算单仅允许终期结算 FINAL");
        }
        validateDeduction(settlement.getDeductionAmount());
        BigDecimal performanceCeiling = snapshot.effectiveContractAmount()
                .add(snapshot.confirmedVariationAmount());
        if (snapshot.finalAmount().compareTo(BigDecimal.ZERO) <= 0
                || snapshot.finalAmount().compareTo(performanceCeiling) > 0) {
            throw new BusinessException("SETTLEMENT_AMOUNT_OUT_OF_CONTRACT",
                    "终期结算金额必须大于0且不得超过合同当前金额与已确认签证之和");
        }
        if (snapshot.unpaidAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("SETTLEMENT_OVERPAYMENT_RECONCILIATION_REQUIRED",
                    "累计付款已超过结算应付余额，必须先完成退款或冲销核对后再提交终期结算");
        }
        long cleanAttachmentCount = fileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, tenantId)
                .eq(SysFile::getBusinessType, "SETTLEMENT")
                .eq(SysFile::getBusinessId, settlement.getId())
                .eq(SysFile::getVirusScanStatus, "CLEAN"));
        if (cleanAttachmentCount == 0) {
            throw new BusinessException("SETTLEMENT_ATTACHMENT_REQUIRED", "终期结算必须上传至少一份已通过安全扫描的结算附件");
        }
    }

    private void validateSettlementContract(CtContract contract) {
        String contractType = contract.getContractType() == null
                ? "" : contract.getContractType().trim().toUpperCase();
        if (!Set.of("SUB", "SUBCONTRACT").contains(contractType)
                || !"APPROVED".equals(contract.getApprovalStatus())
                || !"PERFORMING".equals(contract.getContractStatus())) {
            throw new BusinessException("SETTLEMENT_CONTRACT_INVALID",
                    "终期结算必须关联已审批且履约中的分包合同");
        }
    }

    private LockedSettlementSources lockSettlementSources(StlSettlement settlement) {
        Long tenantId = settlement.getTenantId();
        CtContract contract = ctContractMapper.selectByIdForUpdate(settlement.getContractId(), tenantId);
        if (contract == null || !Objects.equals(contract.getProjectId(), settlement.getProjectId())) {
            throw new BusinessException("STL_SETTLEMENT_CONTRACT_SCOPE_INVALID",
                    "结算单项目与合同项目不一致");
        }
        validateSettlementContract(contract);

        List<VarOrder> variations = varOrderMapper.selectList(new LambdaQueryWrapper<VarOrder>()
                .eq(VarOrder::getTenantId, tenantId)
                .eq(VarOrder::getProjectId, settlement.getProjectId())
                .eq(VarOrder::getContractId, settlement.getContractId())
                .eq(VarOrder::getDirection, "COST")
                .eq(VarOrder::getOwnerConfirmFlag, 1)
                .orderByAsc(VarOrder::getId)
                .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        List<SubMeasure> measures = subMeasureMapper.selectList(new LambdaQueryWrapper<SubMeasure>()
                .eq(SubMeasure::getTenantId, tenantId)
                .eq(SubMeasure::getProjectId, settlement.getProjectId())
                .eq(SubMeasure::getContractId, settlement.getContractId())
                .eq(SubMeasure::getPartnerId, settlement.getPartnerId())
                .eq(SubMeasure::getApprovalStatus, "APPROVED")
                .orderByAsc(SubMeasure::getId)
                .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        List<PayRecord> payments = payRecordMapper.selectList(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, tenantId)
                .eq(PayRecord::getProjectId, settlement.getProjectId())
                .eq(PayRecord::getContractId, settlement.getContractId())
                .eq(PayRecord::getPayStatus, "SUCCESS")
                .orderByAsc(PayRecord::getId)
                .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        if (measures.isEmpty()) {
            throw new BusinessException("SETTLEMENT_APPROVED_MEASURE_REQUIRED", "终期结算前必须至少存在一笔已审批分包计量");
        }
        if (measures.stream().anyMatch(measure ->
                SettlementAmountPolicy.money(measure.getApprovedAmount()).compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException("STL_SETTLEMENT_SOURCE_AMOUNT_INVALID",
                    "计量来源审定金额必须大于0");
        }
        SettlementAmountSnapshot amounts = SettlementAmountPolicy.calculate(
                contract.getCurrentAmount(),
                variations.stream()
                        .map(VarOrder::getConfirmedAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                measures.stream()
                        .map(SubMeasure::getApprovedAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                settlement.getDeductionAmount(),
                payments.stream()
                        .map(PayRecord::getPayAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        return new LockedSettlementSources(contract, measures, amounts);
    }

    private void snapshotApprovedMeasures(
            StlSettlement settlement, List<SubMeasure> lockedMeasures) {
        Long tenantId = settlement.getTenantId();
        List<ContractItemSnapshot> contractItemSnapshots =
                approvedContractItemSnapshots(settlement, lockedMeasures);
        if (contractItemSnapshots.isEmpty()) {
            throw new BusinessException("STL_SETTLEMENT_SOURCE_ITEM_REQUIRED",
                    "已审批分包计量缺少可结算合同清单明细");
        }
        settlementSubMeasureMapper.delete(new LambdaQueryWrapper<SettlementSubMeasure>()
                .eq(SettlementSubMeasure::getTenantId, tenantId)
                .eq(SettlementSubMeasure::getSettlementId, settlement.getId()));
        stlSettlementItemMapper.delete(new LambdaQueryWrapper<StlSettlementItem>()
                .eq(StlSettlementItem::getTenantId, tenantId)
                .eq(StlSettlementItem::getSettlementId, settlement.getId()));
        for (SubMeasure measure : lockedMeasures) {
            SettlementSubMeasure relation = new SettlementSubMeasure();
            relation.setTenantId(tenantId);
            relation.setSettlementId(settlement.getId());
            relation.setSubMeasureId(measure.getId());
            relation.setReportedAmountSnapshot(SettlementAmountPolicy.money(measure.getReportedAmount()));
            relation.setApprovedAmountSnapshot(SettlementAmountPolicy.money(measure.getApprovedAmount()));
            relation.setDeductionAmountSnapshot(SettlementAmountPolicy.money(measure.getDeductionAmount()));
            relation.setNetAmountSnapshot(SettlementAmountPolicy.money(measure.getNetAmount()));
            relation.setCreatedBy(UserContext.getCurrentUserId());
            relation.setCreatedAt(java.time.LocalDateTime.now());
            settlementSubMeasureMapper.insert(relation);
        }
        for (ContractItemSnapshot source : contractItemSnapshots) {
            stlSettlementItemMapper.insert(toSettlementItem(settlement.getId(), source, tenantId));
        }
    }

    private List<ContractItemSnapshot> approvedContractItemSnapshots(
            StlSettlement settlement, List<SubMeasure> measures) {
        Long tenantId = settlement.getTenantId();
        if (measures.isEmpty()) {
            return List.of();
        }
        List<Long> measureIds = measures.stream().map(SubMeasure::getId).toList();
        Map<Long, BigDecimal> quantities = new java.util.TreeMap<>();
        for (SubMeasureItem item : subMeasureItemMapper.selectList(
                new LambdaQueryWrapper<SubMeasureItem>()
                        .eq(SubMeasureItem::getTenantId, tenantId)
                        .in(SubMeasureItem::getMeasureId, measureIds)
                        .orderByAsc(SubMeasureItem::getId)
                        .last("FOR UPDATE"))) { // SQL-SAFETY: fixed-sql-fragment
            if (item.getContractItemId() != null && item.getCurrentQuantity() != null) {
                quantities.merge(item.getContractItemId(), item.getCurrentQuantity(), BigDecimal::add);
            }
        }
        List<ContractItemSnapshot> snapshots = new ArrayList<>(quantities.size());
        for (Map.Entry<Long, BigDecimal> entry : quantities.entrySet()) {
            CtContractItem contractItem = ctContractItemMapper.selectByIdForUpdate(entry.getKey(), tenantId);
            if (contractItem == null || !Objects.equals(contractItem.getTenantId(), tenantId)
                    || !Objects.equals(contractItem.getContractId(), settlement.getContractId())
                    || entry.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("STL_SETTLEMENT_SOURCE_SCOPE_INVALID",
                        "合同清单不存在、无有效已审批计量或不属于当前结算范围");
            }
            snapshots.add(new ContractItemSnapshot(contractItem, entry.getValue()));
        }
        return snapshots;
    }

    private List<ContractItemSnapshot> approvedContractItemSnapshots(StlSettlement settlement) {
        Long tenantId = settlement.getTenantId();
        List<SubMeasure> measures = subMeasureMapper.selectList(new LambdaQueryWrapper<SubMeasure>()
                .eq(SubMeasure::getTenantId, tenantId)
                .eq(SubMeasure::getProjectId, settlement.getProjectId())
                .eq(SubMeasure::getContractId, settlement.getContractId())
                .eq(SubMeasure::getPartnerId, settlement.getPartnerId())
                .eq(SubMeasure::getApprovalStatus, "APPROVED")
                .orderByAsc(SubMeasure::getId));
        return approvedContractItemSnapshots(settlement, measures);
    }

    private StlSettlementItem toSettlementItem(
            Long settlementId, ContractItemSnapshot source, Long tenantId) {
        CtContractItem contractItem = source.contractItem();
        BigDecimal quantity = source.quantity();
        BigDecimal unitPrice = contractItem.getUnitPrice() == null
                ? BigDecimal.ZERO : contractItem.getUnitPrice();
        BigDecimal amount = quantity.multiply(unitPrice)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        StlSettlementItem item = new StlSettlementItem();
        item.setTenantId(tenantId);
        item.setSettlementId(settlementId);
        item.setItemName(contractItem.getItemName());
        item.setUnit(contractItem.getUnit());
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setAmount(amount);
        item.setSourceType("CT_CONTRACT");
        item.setSourceId(contractItem.getId());
        return item;
    }

    private void autoFillAmounts(StlSettlement settlement, CtContract contract) {
        if (contract == null) return;
        validateDeduction(settlement.getDeductionAmount());

        Long tenantId = settlement.getTenantId() != null ? settlement.getTenantId() : UserContext.getCurrentTenantId();
        Long contractId = contract.getId();

        SettlementAmountSnapshot snapshot = SettlementAmountPolicy.calculate(
                contract.getCurrentAmount(),
                queryService.sumVarOrderConfirmed(tenantId, contract.getProjectId(), contractId),
                queryService.sumSubMeasureApproved(tenantId, contract.getProjectId(), contractId),
                settlement.getDeductionAmount(),
                queryService.sumPaidAmount(tenantId, contract.getProjectId(), contractId));

        settlement.setContractAmount(snapshot.effectiveContractAmount());
        settlement.setChangeAmount(snapshot.confirmedVariationAmount());
        settlement.setMeasuredAmount(snapshot.approvedMeasuredAmount());
        settlement.setDeductionAmount(snapshot.deductionAmount());
        settlement.setPaidAmount(snapshot.paidAmount());
        settlement.setFinalAmount(snapshot.finalAmount());
        settlement.setWarrantyAmount(snapshot.warrantyAmount());
        settlement.setUnpaidAmount(snapshot.unpaidAmount());
        settlement.setAmountFormulaVersion(snapshot.formulaVersion());
    }

    private void applyAmountSnapshot(
            StlSettlement settlement, SettlementAmountSnapshot snapshot) {
        settlement.setContractAmount(snapshot.effectiveContractAmount());
        settlement.setChangeAmount(snapshot.confirmedVariationAmount());
        settlement.setMeasuredAmount(snapshot.approvedMeasuredAmount());
        settlement.setDeductionAmount(snapshot.deductionAmount());
        settlement.setPaidAmount(snapshot.paidAmount());
        settlement.setFinalAmount(snapshot.finalAmount());
        settlement.setWarrantyAmount(snapshot.warrantyAmount());
        settlement.setUnpaidAmount(snapshot.unpaidAmount());
        settlement.setAmountFormulaVersion(snapshot.formulaVersion());
    }

    private void validateDeduction(BigDecimal deductionAmount) {
        if (deductionAmount != null && deductionAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("STL_SETTLEMENT_DEDUCTION_INVALID", "扣款金额不能小于0");
        }
    }

    private record ContractItemSnapshot(CtContractItem contractItem, BigDecimal quantity) {
    }

    private record LockedSettlementSources(
            CtContract contract, List<SubMeasure> measures, SettlementAmountSnapshot amounts) {
    }
}
