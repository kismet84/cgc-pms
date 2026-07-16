package com.cgcpms.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
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
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
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
import static com.cgcpms.settlement.constant.SettlementStatusConstants.STATUS_DRAFT;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
    private final SysFileMapper fileMapper;
    private final PmProjectMapper projectMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final WfInstanceMapper wfInstanceMapper;

    // ================================================================
    // Create
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Long create(StlSettlement settlement) {
        Long tenantId = UserContext.getCurrentTenantId();
        settlement.setTenantId(tenantId);

        CtContract contract = validateAndGetContract(settlement.getContractId(), tenantId, settlement.getProjectId());
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
        String prefix = "STL-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";

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
        settlement.setSettlementType("FINAL");

        // Auto-compute amounts
        autoFillAmounts(settlement, contract);

        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            settlement.setSettlementCode(nextSettlementCode(tenantId, prefix, attempt));
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

    private String nextSettlementCode(Long tenantId, String prefix, int offset) {
        LambdaQueryWrapper<StlSettlement> codeWrapper = new LambdaQueryWrapper<>();
        codeWrapper.eq(StlSettlement::getTenantId, tenantId)
                .likeRight(StlSettlement::getSettlementCode, prefix)
                .orderByDesc(StlSettlement::getSettlementCode);
        Page<StlSettlement> page = new Page<>(0, 1);
        Page<StlSettlement> result = stlSettlementMapper.selectPage(page, codeWrapper);
        StlSettlement last = result.getRecords().isEmpty() ? null : result.getRecords().get(0);
        int seq = 1 + offset;
        if (last != null && last.getSettlementCode() != null && last.getSettlementCode().startsWith(prefix)) {
            try {
                seq = Integer.parseInt(last.getSettlementCode().substring(last.getSettlementCode().lastIndexOf('-') + 1)) + 1 + offset;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", last.getSettlementCode(), e);
            }
        }
        return prefix + String.format("%03d", seq);
    }

    // ================================================================
    // Update / Delete
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void update(StlSettlement settlement) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement existing = queryService.validateAndGetSettlement(settlement.getId());
        if (!Set.of(APPROVAL_DRAFT, SettlementStatusConstants.APPROVAL_REJECTED)
                .contains(existing.getApprovalStatus())) {
            throw new BusinessException("STL_SETTLEMENT_IN_APPROVAL", "结算单审批中或已审批，不可编辑");
        }

        if (settlement.getContractId() != null && !Objects.equals(settlement.getContractId(), existing.getContractId())) {
            validateAndGetContract(settlement.getContractId(), tenantId, existing.getProjectId());
        }

        CtContract contract = ctContractMapper.selectById(existing.getContractId());
        autoFillAmounts(settlement, contract);

        stlSettlementMapper.updateById(settlement);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement existing = queryService.validateAndGetSettlement(id);
        if (!Set.of(APPROVAL_DRAFT, SettlementStatusConstants.APPROVAL_REJECTED)
                .contains(existing.getApprovalStatus())) {
            throw new BusinessException("STL_SETTLEMENT_IN_APPROVAL", "结算单审批中或已审批，不可删除");
        }

        stlSettlementItemMapper.delete(new LambdaQueryWrapper<StlSettlementItem>()
                .eq(StlSettlementItem::getTenantId, tenantId)
                .eq(StlSettlementItem::getSettlementId, id));
        settlementSubMeasureMapper.delete(new LambdaQueryWrapper<SettlementSubMeasure>()
                .eq(SettlementSubMeasure::getTenantId, tenantId)
                .eq(SettlementSubMeasure::getSettlementId, id));
        stlSettlementMapper.deleteById(id);
    }

    // ================================================================
    // Items management
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long settlementId, List<StlSettlementItem> items) {
        Long tenantId = UserContext.getCurrentTenantId();
        StlSettlement settlement = queryService.validateAndGetSettlement(settlementId);
        if (!Set.of(APPROVAL_DRAFT, SettlementStatusConstants.APPROVAL_REJECTED)
                .contains(settlement.getApprovalStatus())) {
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

        validateSettlementIntegrity(settlement);

        // Recompute amount snapshot before approval
        CtContract contract = ctContractMapper.selectById(settlement.getContractId());
        autoFillAmounts(settlement, contract);
        snapshotApprovedMeasures(settlement);

        stlSettlementMapper.update(null, new LambdaUpdateWrapper<StlSettlement>()
                .eq(StlSettlement::getId, settlementId)
                .set(StlSettlement::getApprovalStatus, APPROVAL_APPROVING)
                .set(StlSettlement::getStatus, APPROVAL_APPROVING));

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
        return contract;
    }

    private void validateSettlementIntegrity(StlSettlement settlement) {
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
        CtContract contract = validateAndGetContract(settlement.getContractId(), tenantId, settlement.getProjectId());
        String contractType = contract.getContractType() == null ? "" : contract.getContractType().trim().toUpperCase();
        if (!Set.of("SUB", "SUBCONTRACT").contains(contractType)
                || !"APPROVED".equals(contract.getApprovalStatus())
                || !"PERFORMING".equals(contract.getContractStatus())) {
            throw new BusinessException("SETTLEMENT_CONTRACT_INVALID", "终期结算必须关联已审批且履约中的分包合同");
        }
        if (!Objects.equals(contract.getPartyBId(), settlement.getPartnerId())) {
            throw new BusinessException("SETTLEMENT_PARTNER_MISMATCH", "结算分包商必须等于分包合同乙方");
        }
        if (!"FINAL".equalsIgnoreCase(settlement.getSettlementType())) {
            throw new BusinessException("SETTLEMENT_TYPE_INVALID", "本闭环结算单仅允许终期结算 FINAL");
        }
        SettlementAmountSnapshot snapshot = SettlementAmountPolicy.calculate(
                contract.getCurrentAmount(),
                queryService.sumVarOrderConfirmed(tenantId, contract.getId()),
                queryService.sumSubMeasureApproved(tenantId, contract.getId()),
                settlement.getDeductionAmount(),
                queryService.sumPaidAmount(tenantId, contract.getId()));
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

    private void snapshotApprovedMeasures(StlSettlement settlement) {
        Long tenantId = settlement.getTenantId();
        List<SubMeasure> measures = subMeasureMapper.selectList(new LambdaQueryWrapper<SubMeasure>()
                .eq(SubMeasure::getTenantId, tenantId)
                .eq(SubMeasure::getProjectId, settlement.getProjectId())
                .eq(SubMeasure::getContractId, settlement.getContractId())
                .eq(SubMeasure::getPartnerId, settlement.getPartnerId())
                .eq(SubMeasure::getApprovalStatus, "APPROVED")
                .orderByAsc(SubMeasure::getMeasureDate, SubMeasure::getCreatedAt));
        if (measures.isEmpty()) {
            throw new BusinessException("SETTLEMENT_APPROVED_MEASURE_REQUIRED", "终期结算前必须至少存在一笔已审批分包计量");
        }
        settlementSubMeasureMapper.delete(new LambdaQueryWrapper<SettlementSubMeasure>()
                .eq(SettlementSubMeasure::getTenantId, tenantId)
                .eq(SettlementSubMeasure::getSettlementId, settlement.getId()));
        for (SubMeasure measure : measures) {
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
    }

    private void autoFillAmounts(StlSettlement settlement, CtContract contract) {
        if (contract == null) return;

        Long tenantId = settlement.getTenantId() != null ? settlement.getTenantId() : UserContext.getCurrentTenantId();
        Long contractId = contract.getId();

        SettlementAmountSnapshot snapshot = SettlementAmountPolicy.calculate(
                contract.getCurrentAmount(),
                queryService.sumVarOrderConfirmed(tenantId, contractId),
                queryService.sumSubMeasureApproved(tenantId, contractId),
                settlement.getDeductionAmount(),
                queryService.sumPaidAmount(tenantId, contractId));

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
}
