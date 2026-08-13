package com.cgcpms.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.ContractBudgetAllocation;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.mapper.ContractBudgetAllocationMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.budget.service.ContractBudgetAllocationService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.dto.ContractSaveRequest;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.vo.ContractApprovalRecordVO;
import com.cgcpms.contract.vo.ContractPerformanceReportVO;
import com.cgcpms.contract.vo.CtContractVO;
import com.cgcpms.file.service.FileLifecycleGateway;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.system.dict.service.SysDictDataService;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfRecordMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import com.cgcpms.common.util.CodeGenerationService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class CtContractService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Set<String> PURCHASE_PRICING_MODES = Set.of("FIXED", "ACTUAL");

    private final CtContractMapper ctContractMapper;
    private final ContractBudgetAllocationMapper contractBudgetAllocationMapper;
    private final ContractBudgetAllocationService contractBudgetAllocationService;
    private final ProjectBudgetMapper projectBudgetMapper;
    private final PayApplicationMapper payApplicationMapper;
    private final PayRecordMapper payRecordMapper;
    private final StlSettlementMapper settlementMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final CtContractItemService itemService;
    private final CtContractPaymentTermService paymentTermService;
    private final WorkflowEngine workflowEngine;
    private final WfInstanceMapper wfInstanceMapper;
    private final CodeGenerationService codeGenerationService;
    private final ProjectAccessChecker projectAccessChecker;
    private final SysDictDataService sysDictDataService;
    private final FileLifecycleGateway fileLifecycleGateway;
    private final CtContractQueryOperations queryOperations;
    private final CtContractPerformanceSettlement performanceSettlement;

    public CtContractService(CtContractMapper ctContractMapper,
                             CtContractChangeMapper ctContractChangeMapper,
                             ContractBudgetAllocationMapper contractBudgetAllocationMapper,
                             ContractBudgetAllocationService contractBudgetAllocationService,
                             ProjectBudgetMapper projectBudgetMapper,
                             PayApplicationMapper payApplicationMapper,
                             PayRecordMapper payRecordMapper,
                             StlSettlementMapper settlementMapper,
                             PmProjectMapper pmProjectMapper,
                             MdPartnerMapper mdPartnerMapper,
                             CtContractItemService itemService,
                             CtContractPaymentTermService paymentTermService,
                             WorkflowEngine workflowEngine,
                             WfInstanceMapper wfInstanceMapper,
                             WfRecordMapper wfRecordMapper,
                             CodeGenerationService codeGenerationService,
                             ProjectAccessChecker projectAccessChecker,
                             SysDictDataService sysDictDataService,
                             FileLifecycleGateway fileLifecycleGateway,
                             JdbcTemplate jdbcTemplate) {
        this.ctContractMapper = ctContractMapper;
        this.contractBudgetAllocationMapper = contractBudgetAllocationMapper;
        this.contractBudgetAllocationService = contractBudgetAllocationService;
        this.projectBudgetMapper = projectBudgetMapper;
        this.payApplicationMapper = payApplicationMapper;
        this.payRecordMapper = payRecordMapper;
        this.settlementMapper = settlementMapper;
        this.pmProjectMapper = pmProjectMapper;
        this.mdPartnerMapper = mdPartnerMapper;
        this.itemService = itemService;
        this.paymentTermService = paymentTermService;
        this.workflowEngine = workflowEngine;
        this.wfInstanceMapper = wfInstanceMapper;
        this.codeGenerationService = codeGenerationService;
        this.projectAccessChecker = projectAccessChecker;
        this.sysDictDataService = sysDictDataService;
        this.fileLifecycleGateway = fileLifecycleGateway;
        this.queryOperations = new CtContractQueryOperations(
                ctContractMapper, ctContractChangeMapper, projectBudgetMapper, payRecordMapper,
                pmProjectMapper, mdPartnerMapper, wfInstanceMapper, wfRecordMapper, projectAccessChecker);
        this.performanceSettlement = new CtContractPerformanceSettlement(
                ctContractMapper, projectAccessChecker, jdbcTemplate);
    }

    public List<ContractProjectOption> getProjectOptions() {
        return queryOperations.getProjectOptions();
    }

    public IPage<CtContractVO> getPage(long pageNo, long pageSize, String keyword,
                                       String contractCode, String contractName,
                                       String contractType, String contractStatus, String approvalStatus,
                                       Long projectId, Long partyAId, Long partyBId,
                                       LocalDate startDate, LocalDate endDate) {
        return queryOperations.getPage(pageNo, pageSize, keyword, contractCode, contractName,
                contractType, contractStatus, approvalStatus, projectId, partyAId, partyBId,
                startDate, endDate);
    }

    public Map<String, Object> getKpi(String contractCode, String contractName,
                                      String contractType, String contractStatus, String approvalStatus,
                                      Long projectId, Long partyAId, Long partyBId,
                                      LocalDate startDate, LocalDate endDate) {
        return queryOperations.getKpi(contractCode, contractName, contractType, contractStatus,
                approvalStatus, projectId, partyAId, partyBId, startDate, endDate);
    }

    public ContractPerformanceReportVO getPerformanceReport(Long projectId) {
        return queryOperations.getPerformanceReport(projectId);
    }

    public CtContractVO getById(Long id) {
        return queryOperations.getById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CtContract contract) {
        sanitizeContractForCreate(contract);
        normalizeContractType(contract);
        validatePurchasePricingMode(contract);
        deriveContractFinancials(contract);
        validateContractReferences(contract, "创建合同");
        validateContractCoreFinancials(contract);
        validateProjectBudgetGate(contract);

        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            contract.setContractCode(nextContractCode(attempt));
            try {
                ctContractMapper.insert(contract);
                return contract.getId();
            } catch (DuplicateKeyException e) {
                log.warn("合同编号冲突，重试生成 contractCode={}", contract.getContractCode());
            }
        }
        throw new BusinessException("CONTRACT_CODE_CONFLICT", "合同编号生成冲突，请重试");
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CtContract contract) {
        CtContract existing = requireEditableContract(contract.getId(), "编辑合同");
        ensureClientVersionMatches(contract.getVersion(), existing.getVersion());
        normalizeContractType(contract);
        validatePurchasePricingMode(contract);
        deriveContractFinancials(contract);
        validateContractReferences(contract, "编辑合同");
        validateContractCoreFinancials(contract);
        validateProjectBudgetGate(contract);

        // 校验合同状态：仅允许在有效状态集合内修改
        if (contract.getContractStatus() != null
                && !Set.of(ContractStatusConstants.STATUS_DRAFT,
                           ContractStatusConstants.STATUS_PERFORMING,
                           ContractStatusConstants.STATUS_TERMINATED)
                        .contains(contract.getContractStatus())) {
            throw new BusinessException("CONTRACT_STATUS_INVALID", "合同状态不合法");
        }

        updateEditableContract(contract, existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void settlePerformance(Long contractId, Integer clientVersion) {
        performanceSettlement.settlePerformance(contractId, clientVersion);
    }

    /**
     * 提交合同审批。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long contractId) {
        throw new BusinessException("CONTRACT_VERSION_REQUIRED", "提交合同审批必须携带最新版本号");
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long contractId, Integer clientVersion) {
        CtContract contract = ctContractMapper.selectById(contractId);
        if (contract == null || !contract.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        if (contract.getProjectId() != null) {
            projectAccessChecker.checkAccess(contract.getProjectId(), "提交合同审批");
        }
        ensureClientVersionMatches(clientVersion, contract.getVersion());

        boolean resubmit = ContractStatusConstants.APPROVAL_REJECTED.equals(contract.getApprovalStatus());
        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(contract.getApprovalStatus()) && !resubmit)
            throw new BusinessException("CONTRACT_ALREADY_SUBMITTED", "合同已提交审批，不可重复提交");

        // 必须有合同编号
        if (contract.getContractCode() == null || contract.getContractCode().isBlank())
            throw new BusinessException("CONTRACT_NO_CODE", "合同编号不能为空，无法提交审批");

        validatePurchaseSupplierAdmission(contract);
        validateContractReferences(contract, "提交合同审批");
        validateContractCoreFinancials(contract);
        validateProjectBudgetGate(contract);
        validatePurchasePricingConfiguration(contract);
        contractBudgetAllocationService.validateForContractSubmit(contractId);

        // 更新审批状态为审批中（携带版本号乐观锁）
        LambdaUpdateWrapper<CtContract> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(CtContract::getId, contractId)
                .eq(CtContract::getVersion, contract.getVersion())
                .set(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVING)
                .set(CtContract::getVersion, contract.getVersion() + 1);
        int updated = ctContractMapper.update(null, updateWrapper);
        if (updated != 1) {
            throw new BusinessException("CONTRACT_VERSION_CONFLICT", "合同已被其他用户修改，请刷新后重试");
        }

        // 调用审批引擎
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        if (resubmit) {
            WfInstance existingInstance = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                    .eq(WfInstance::getTenantId, tenantId)
                    .eq(WfInstance::getBusinessType, ContractStatusConstants.BUSINESS_TYPE_CONTRACT_APPROVAL)
                    .eq(WfInstance::getBusinessId, contractId));
            if (existingInstance == null) {
                throw new BusinessException("CONTRACT_WORKFLOW_INSTANCE_NOT_FOUND", "驳回合同缺少原审批实例");
            }
            workflowEngine.resubmit(existingInstance.getId(), userId, username);
        } else {
            workflowEngine.submit(userId, username, tenantId,
                    ContractStatusConstants.BUSINESS_TYPE_CONTRACT_APPROVAL,
                    contractId,
                    contract.getContractName(),
                    contract.getContractAmount(),
                    contract.getProjectId(),
                    contractId,
                    null, null, null);
        }
    }

    /**
     * 删除合同（软删除，仅限 DRAFT 状态）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CtContract existing = ctContractMapper.selectByIdForUpdate(id, UserContext.getCurrentTenantId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        if (existing.getProjectId() != null) {
            projectAccessChecker.checkAccess(existing.getProjectId(), "删除合同");
        }

        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(existing.getApprovalStatus()))
            throw new BusinessException("CONTRACT_IN_APPROVAL", "合同审批中或已审批，不可删除");

        Long tenantId = existing.getTenantId();
        boolean hasDependencies =
                contractBudgetAllocationMapper.selectCount(new LambdaQueryWrapper<ContractBudgetAllocation>()
                        .eq(ContractBudgetAllocation::getTenantId, tenantId)
                        .eq(ContractBudgetAllocation::getContractId, id)) > 0
                || payApplicationMapper.selectCount(new LambdaQueryWrapper<PayApplication>()
                        .eq(PayApplication::getTenantId, tenantId)
                        .eq(PayApplication::getContractId, id)) > 0
                || payRecordMapper.selectCount(new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getTenantId, tenantId)
                        .eq(PayRecord::getContractId, id)) > 0
                || settlementMapper.selectCount(new LambdaQueryWrapper<StlSettlement>()
                        .eq(StlSettlement::getTenantId, tenantId)
                        .eq(StlSettlement::getContractId, id)) > 0
                || wfInstanceMapper.selectCount(new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getTenantId, tenantId)
                        .eq(WfInstance::getBusinessId, id)) > 0;
        if (hasDependencies) {
            throw new BusinessException("CONTRACT_HAS_DEPENDENCIES", "合同已被预算、付款、结算或审批引用，不可删除");
        }

        fileLifecycleGateway.deleteAllForBusinessCascade("CONTRACT", id);
        ctContractMapper.deleteById(id);
    }

    /**
     * 复合原子保存：合同头 + 明细项 + 付款条款 在同一事务内创建/更新。
     * <p>
     * contract.id == null → 新建（生成合同编号，置 DRAFT）
     * contract.id != null → 更新已有合同
     * <p>
     * 所有子表采用 delete-then-insert 策略，与现有 batchSave 行为一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long compositeSave(ContractSaveRequest request) {
        CtContract contract = request.getContract();
        if (contract == null) {
            throw new BusinessException("CONTRACT_REQUIRED", "合同不能为空");
        }
        List<CtContractItem> items = request.getItems();
        List<CtContractPaymentTerm> terms = request.getPaymentTerms();

        if (contract.getId() == null) {
            // ── 新建 ──
            sanitizeContractForCreate(contract);
            normalizeContractType(contract);
            validatePurchasePricingMode(contract);
            deriveCompositeFinancials(contract, items);
            validateContractReferences(contract, "创建合同");
            validateCompositeFinancials(contract, items, terms);
            validateProjectBudgetGate(contract);

            for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
                contract.setContractCode(nextContractCode(attempt));
                try {
                    ctContractMapper.insert(contract);
                    break;
                } catch (DuplicateKeyException e) {
                    log.warn("合同编号冲突，重试生成 contractCode={}", contract.getContractCode());
                    if (attempt == CODE_GENERATION_MAX_RETRIES - 1) {
                        throw new BusinessException("CONTRACT_CODE_CONFLICT", "合同编号生成冲突，请重试");
                    }
                }
            }
        } else {
            // ── 更新 ──
            CtContract existing = requireEditableContract(contract.getId(), "编辑合同");
            ensureClientVersionMatches(contract.getVersion(), existing.getVersion());
            normalizeContractType(contract);
            validatePurchasePricingMode(contract);
            deriveCompositeFinancials(contract, items);
            validateContractReferences(contract, "编辑合同");
            validateCompositeFinancials(contract, items, terms);
            validateProjectBudgetGate(contract);
            updateEditableContract(contract, existing);
        }

        Long contractId = contract.getId();

        // ── 批量保存明细项（delete-then-insert）──
        if (items != null) {
            itemService.batchSave(contractId, items);
        }

        // ── 批量保存付款条款（delete-then-insert）──
        if (terms != null) {
            paymentTermService.batchSave(contractId, terms);
        }

        return contractId;
    }

    private String nextContractCode(int offset) {
        return codeGenerationService.nextCode(
                ctContractMapper,
                CtContract::getContractCode,
                "CT-",
                UserContext.getCurrentTenantId(),
                true,  // includeDeleted 避免软删除 UK 冲突
                offset
        );
    }

    /**
     * 查询合同审批记录（含租户隔离）。
     */
    public List<ContractApprovalRecordVO> getApprovalRecords(Long contractId) {
        return queryOperations.getApprovalRecords(contractId);
    }

    private void validateContractReferences(CtContract contract, String action) {
        if (contract == null || contract.getPartyAId() == null || contract.getPartyBId() == null) {
            throw new BusinessException("CONTRACT_PARTY_REQUIRED", "合同甲方和乙方不能为空");
        }
        if (java.util.Objects.equals(contract.getPartyAId(), contract.getPartyBId())) {
            throw new BusinessException("CONTRACT_PARTIES_SAME", "合同甲方和乙方不能相同");
        }
        if (contract.getProjectId() == null) {
            throw new BusinessException("CONTRACT_PROJECT_REQUIRED", "关联合同项目不能为空");
        }

        Long tenantId = UserContext.getCurrentTenantId();
        PmProject project = pmProjectMapper.selectById(contract.getProjectId());
        if (project == null || !java.util.Objects.equals(project.getTenantId(), tenantId)) {
            throw new BusinessException("CONTRACT_PROJECT_NOT_FOUND", "关联合同项目不存在");
        }
        projectAccessChecker.checkAccess(project, action);
        MdPartner partyA = mdPartnerMapper.selectById(contract.getPartyAId());
        if (partyA == null || !java.util.Objects.equals(partyA.getTenantId(), tenantId)) {
            throw new BusinessException("CONTRACT_PARTY_A_NOT_FOUND", "合同甲方不存在");
        }
        MdPartner partyB = mdPartnerMapper.selectById(contract.getPartyBId());
        if (partyB == null || !java.util.Objects.equals(partyB.getTenantId(), tenantId)) {
            throw new BusinessException("CONTRACT_PARTY_B_NOT_FOUND", "合同乙方不存在");
        }
    }

    private void validateProjectBudgetGate(CtContract contract) {
        Long tenantId = UserContext.getCurrentTenantId();
        PmProject project = pmProjectMapper.selectById(contract.getProjectId());
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw new BusinessException("CONTRACT_PROJECT_NOT_FOUND", "关联合同项目不存在");
        }
        if ("MAIN".equals(contract.getContractType())) {
            if (!"APPROVED".equals(project.getApprovalStatus())
                    || !Set.of(ProjectStatusConstants.PREPARING, ProjectStatusConstants.ACTIVE).contains(project.getStatus())) {
                throw new BusinessException("MAIN_CONTRACT_PROJECT_NOT_READY", "MAIN合同只能在已批准的筹备或在建项目中编审");
            }
            return;
        }
        if (!ProjectStatusConstants.ACTIVE.equals(project.getStatus())) {
            throw new BusinessException("PROJECT_NOT_ACTIVE", "非MAIN合同只能在进行中的项目创建或提交");
        }
        long activeBudgetCount = projectBudgetMapper.selectCount(new LambdaQueryWrapper<ProjectBudget>()
                .eq(ProjectBudget::getTenantId, tenantId)
                .eq(ProjectBudget::getProjectId, contract.getProjectId())
                .eq(ProjectBudget::getStatus, BudgetStatusConstants.STATUS_ACTIVE)
                .eq(ProjectBudget::getActiveFlag, 1));
        if (activeBudgetCount == 0) {
            throw new BusinessException("BUDGET_NOT_ACTIVE", "项目必须存在当前生效预算才能创建或提交合同");
        }
    }

    private void normalizeContractType(CtContract contract) {
        if (contract == null) {
            throw new BusinessException("CONTRACT_REQUIRED", "合同不能为空");
        }
        contract.setContractType(sysDictDataService.requireEnabledValue(
                "contract_type", contract.getContractType(),
                "CONTRACT_TYPE_INVALID", "合同类型不合法"));
    }

    private void validatePurchaseSupplierAdmission(CtContract contract) {
        if (!"PURCHASE".equals(contract.getContractType())) return;
        MdPartner supplier = mdPartnerMapper.selectById(contract.getPartyBId());
        if (supplier == null || !java.util.Objects.equals(supplier.getTenantId(), UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_SUPPLIER_NOT_FOUND", "采购合同乙方供应商不存在");
        if (!"SUPPLIER".equals(supplier.getPartnerType()))
            throw new BusinessException("PURCHASE_SUPPLIER_TYPE_INVALID", "采购合同乙方必须是供应商");
        if (!"ENABLE".equals(supplier.getStatus()))
            throw new BusinessException("PURCHASE_SUPPLIER_DISABLED", "供应商已停用，禁止提交采购合同审批");
        if (java.util.Objects.equals(supplier.getBlacklistFlag(), 1))
            throw new BusinessException("PURCHASE_SUPPLIER_BLACKLISTED", "黑名单供应商禁止提交采购合同审批");
    }

    public record ContractProjectOption(
            String id,
            String projectCode,
            String projectName,
            String status,
            boolean mainEligible,
            boolean nonMainEligible) {
    }


    private CtContract requireEditableContract(Long contractId, String action) {
        CtContract existing = ctContractMapper.selectById(contractId);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        }
        if (existing.getProjectId() != null) {
            projectAccessChecker.checkAccess(existing.getProjectId(), action);
        }
        if (!List.of(ContractStatusConstants.APPROVAL_DRAFT, ContractStatusConstants.APPROVAL_REJECTED)
                .contains(existing.getApprovalStatus())) {
            throw new BusinessException("CONTRACT_NOT_EDITABLE", "只有草稿或驳回合同可以编辑");
        }
        return existing;
    }

    static void ensureClientVersionMatches(Integer clientVersion, Integer currentVersion) {
        if (clientVersion == null) {
            throw new BusinessException("CONTRACT_VERSION_REQUIRED", "请求必须携带最新版本号");
        }
        if (!Objects.equals(clientVersion, currentVersion)) {
            throw new BusinessException("CONTRACT_VERSION_CONFLICT", "合同已被其他用户修改，请刷新后重试");
        }
    }

    private void sanitizeContractForCreate(CtContract contract) {
        contract.setId(null);
        contract.setContractCode(null);
        contract.setTenantId(UserContext.getCurrentTenantId());
        contract.setApprovalStatus(ContractStatusConstants.APPROVAL_DRAFT);
        contract.setContractStatus(ContractStatusConstants.STATUS_DRAFT);
        contract.setCurrentAmount(contract.getContractAmount());
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setSettlementAmount(BigDecimal.ZERO);
        contract.setCostGeneratedFlag(0);
        contract.setCreatedBy(null);
        contract.setCreatedAt(null);
        contract.setUpdatedAt(null);
        contract.setVersion(null);
    }

    private void updateEditableContract(CtContract contract, CtContract existing) {
        validateActiveProjectContractAmount(existing, contract);
        Integer currentVersion = existing.getVersion();
        int updated = ctContractMapper.update(null,
                new LambdaUpdateWrapper<CtContract>()
                        .eq(CtContract::getId, contract.getId())
                        .eq(CtContract::getVersion, currentVersion)
                        .set(CtContract::getContractName, contract.getContractName())
                        .set(CtContract::getContractType, contract.getContractType())
                        .set(CtContract::getProjectId, contract.getProjectId())
                        .set(CtContract::getOrgId, contract.getOrgId())
                        .set(CtContract::getPartyAId, contract.getPartyAId())
                        .set(CtContract::getPartyBId, contract.getPartyBId())
                        .set(CtContract::getContractAmount, contract.getContractAmount())
                        .set(CtContract::getCurrentAmount, contract.getContractAmount())
                        .set(CtContract::getTaxRate, contract.getTaxRate())
                        .set(CtContract::getTaxAmount, contract.getTaxAmount())
                        .set(CtContract::getAmountWithoutTax, contract.getAmountWithoutTax())
                        .set(CtContract::getSignedDate, contract.getSignedDate())
                        .set(CtContract::getStartDate, contract.getStartDate())
                        .set(CtContract::getEndDate, contract.getEndDate())
                        .set(CtContract::getPaymentMethod, contract.getPaymentMethod())
                        .set(CtContract::getSettlementMethod, contract.getSettlementMethod())
                        .set(CtContract::getContractStatus,
                                contract.getContractStatus() != null ? contract.getContractStatus() : existing.getContractStatus())
                        .set(CtContract::getRemark, contract.getRemark())
                        .set(CtContract::getVersion, currentVersion + 1));
        if (updated != 1) {
            throw new BusinessException("CONTRACT_VERSION_CONFLICT", "合同已被其他用户修改，请刷新后重试");
        }
    }

    private void validateActiveProjectContractAmount(CtContract existing, CtContract requested) {
        BigDecimal persistedAmount = existing.getContractAmount();
        BigDecimal requestedAmount = requested.getContractAmount();
        if (persistedAmount != null && requestedAmount != null
                && persistedAmount.compareTo(requestedAmount) == 0) {
            return;
        }
        PmProject project = pmProjectMapper.selectById(existing.getProjectId());
        if (project != null
                && Objects.equals(project.getTenantId(), UserContext.getCurrentTenantId())
                && ProjectStatusConstants.ACTIVE.equals(project.getStatus())) {
            throw new BusinessException("CONTRACT_AMOUNT_LOCKED",
                    "项目已在建，合同总价不可直接修改，请发起合同变更");
        }
    }

    private void validateCompositeFinancials(CtContract contract, List<CtContractItem> items,
                                             List<CtContractPaymentTerm> terms) {
        validateContractCoreFinancials(contract);

        if (items != null && !items.isEmpty()) {
            BigDecimal totalAmount = BigDecimal.ZERO;
            BigDecimal totalTaxAmount = BigDecimal.ZERO;
            BigDecimal totalAmountWithoutTax = BigDecimal.ZERO;
            for (CtContractItem item : items) {
                requireMoneyField(item.getAmount(), "CONTRACT_ITEM_AMOUNT_REQUIRED", "合同清单金额不能为空");
                requireMoneyField(item.getTaxAmount(), "CONTRACT_ITEM_TAX_AMOUNT_REQUIRED", "合同清单税额不能为空");
                requireMoneyField(item.getAmountWithoutTax(), "CONTRACT_ITEM_AMOUNT_WITHOUT_TAX_REQUIRED", "合同清单不含税金额不能为空");
                requireNonNegative(item.getTaxRate(), "CONTRACT_ITEM_TAX_RATE_INVALID", "合同清单税率不能为负数");
                if (item.getQuantity() != null && item.getUnitPrice() != null
                        && item.getQuantity().multiply(item.getUnitPrice()).compareTo(item.getAmount()) != 0) {
                    throw new BusinessException("CONTRACT_ITEM_AMOUNT_MISMATCH", "合同清单金额必须等于数量乘以单价");
                }
                validateAmountBreakdown(item.getAmount(), item.getTaxAmount(), item.getAmountWithoutTax(),
                        "CONTRACT_ITEM_AMOUNT_BREAKDOWN_MISMATCH", "合同清单含税金额必须等于税额加不含税金额");
                totalAmount = totalAmount.add(item.getAmount());
                totalTaxAmount = totalTaxAmount.add(item.getTaxAmount());
                totalAmountWithoutTax = totalAmountWithoutTax.add(item.getAmountWithoutTax());
            }
            if (totalAmount.compareTo(contract.getContractAmount()) != 0
                    || totalTaxAmount.compareTo(contract.getTaxAmount()) != 0
                    || totalAmountWithoutTax.compareTo(contract.getAmountWithoutTax()) != 0) {
                throw new BusinessException("CONTRACT_ITEMS_TOTAL_MISMATCH", "合同头金额与清单合计不一致");
            }
        }

        if (terms != null && !terms.isEmpty()) {
            BigDecimal totalRatio = BigDecimal.ZERO;
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (CtContractPaymentTerm term : terms) {
                requireMoneyField(term.getPaymentRatio(), "CONTRACT_PAYMENT_RATIO_REQUIRED", "付款条款比例不能为空");
                requireMoneyField(term.getPaymentAmount(), "CONTRACT_PAYMENT_AMOUNT_REQUIRED", "付款条款金额不能为空");
                validatePaymentTermDates(contract, term);
                totalRatio = totalRatio.add(term.getPaymentRatio());
                totalAmount = totalAmount.add(term.getPaymentAmount());
            }
            if (totalAmount.compareTo(contract.getContractAmount()) != 0) {
                throw new BusinessException("CONTRACT_PAYMENT_TERMS_TOTAL_MISMATCH", "付款条款金额合计必须等于合同金额");
            }
            if (totalRatio.compareTo(HUNDRED) != 0) {
                throw new BusinessException("CONTRACT_PAYMENT_TERMS_RATIO_MISMATCH", "付款条款比例合计必须等于100");
            }
        }
    }

    private void validateContractCoreFinancials(CtContract contract) {
        requireMoneyField(contract.getContractAmount(), "CONTRACT_AMOUNT_REQUIRED", "合同金额不能为空");
        requireNonNegative(contract.getCurrentAmount(), "CONTRACT_CURRENT_AMOUNT_INVALID", "合同当前金额不能为负数");
        validateTaxRate(contract.getTaxRate());
        requireMoneyField(contract.getTaxAmount(), "CONTRACT_TAX_AMOUNT_REQUIRED", "合同税额不能为空");
        requireMoneyField(contract.getAmountWithoutTax(), "CONTRACT_AMOUNT_WITHOUT_TAX_REQUIRED", "合同不含税金额不能为空");
        validateAmountBreakdown(contract.getContractAmount(), contract.getTaxAmount(), contract.getAmountWithoutTax(),
                "CONTRACT_AMOUNT_BREAKDOWN_MISMATCH", "合同含税金额必须等于税额加不含税金额");
        validateContractDates(contract);
    }

    private void deriveCompositeFinancials(CtContract contract, List<CtContractItem> items) {
        deriveContractFinancials(contract);
        itemService.deriveFinancials(contract, items);
        if (items == null || items.isEmpty()) {
            return;
        }
        BigDecimal itemAmountTotal = items.stream()
                .map(CtContractItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (itemAmountTotal.compareTo(contract.getContractAmount()) != 0) {
            return;
        }
        contract.setTaxAmount(items.stream()
                .map(CtContractItem::getTaxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        contract.setAmountWithoutTax(items.stream()
                .map(CtContractItem::getAmountWithoutTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void deriveContractFinancials(CtContract contract) {
        requireMoneyField(contract.getContractAmount(), "CONTRACT_AMOUNT_REQUIRED", "合同金额不能为空");
        validateTaxRate(contract.getTaxRate());
        BigDecimal amount = contract.getContractAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal amountWithoutTax = contract.getTaxRate().signum() == 0
                ? amount
                : amount.multiply(HUNDRED).divide(HUNDRED.add(contract.getTaxRate()), 2, RoundingMode.HALF_UP);
        contract.setContractAmount(amount);
        contract.setCurrentAmount(amount);
        contract.setAmountWithoutTax(amountWithoutTax);
        contract.setTaxAmount(amount.subtract(amountWithoutTax));
    }

    private void validateTaxRate(BigDecimal taxRate) {
        if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(HUNDRED) > 0) {
            throw new BusinessException("CONTRACT_TAX_RATE_INVALID", "合同税率必须在0到100之间");
        }
    }

    private void validatePurchasePricingConfiguration(CtContract contract) {
        if (!"PURCHASE".equals(contract.getContractType())) {
            return;
        }
        validatePurchasePricingMode(contract);
        List<CtContractItem> items = itemService.getByContractId(contract.getId());
        if (items.isEmpty()) {
            throw new BusinessException("PURCHASE_CONTRACT_ITEM_REQUIRED", "采购合同至少需要一条物料清单");
        }
        Set<Long> materialIds = new java.util.HashSet<>();
        for (CtContractItem item : items) {
            if (item.getMaterialId() == null) {
                throw new BusinessException("PURCHASE_CONTRACT_MATERIAL_REQUIRED", "采购合同清单必须绑定物料");
            }
            if (!materialIds.add(item.getMaterialId())) {
                throw new BusinessException("PURCHASE_CONTRACT_MATERIAL_DUPLICATED", "同一采购合同内每种物料只能有一条清单");
            }
            if ("FIXED".equals(contract.getPricingMode()) && item.getUnitPrice() == null) {
                throw new BusinessException("PURCHASE_CONTRACT_UNIT_PRICE_REQUIRED", "固定价采购合同清单必须填写单价");
            }
        }
    }

    private void validatePurchasePricingMode(CtContract contract) {
        if ("PURCHASE".equals(contract.getContractType())
                && contract.getPricingMode() != null
                && !PURCHASE_PRICING_MODES.contains(contract.getPricingMode())) {
            throw new BusinessException("PURCHASE_CONTRACT_PRICING_MODE_INVALID", "采购合同计价模式不合法");
        }
    }

    private void validateContractDates(CtContract contract) {
        if (contract.getStartDate() != null && contract.getEndDate() != null
                && contract.getStartDate().isAfter(contract.getEndDate())) {
            throw new BusinessException("CONTRACT_DATE_INVALID", "合同开始日期不能晚于结束日期");
        }
        if (contract.getSignedDate() != null && contract.getEndDate() != null
                && contract.getSignedDate().isAfter(contract.getEndDate())) {
            throw new BusinessException("CONTRACT_SIGNED_DATE_INVALID", "合同签订日期不能晚于结束日期");
        }
    }

    private void validatePaymentTermDates(CtContract contract, CtContractPaymentTerm term) {
        if (term.getPlannedDate() != null && term.getActualDate() != null
                && term.getActualDate().isBefore(term.getPlannedDate())) {
            throw new BusinessException("CONTRACT_PAYMENT_TERM_DATE_INVALID", "付款条款实际日期不能早于计划日期");
        }
        LocalDate startDate = contract.getStartDate();
        LocalDate endDate = contract.getEndDate();
        if (startDate != null && term.getPlannedDate() != null && term.getPlannedDate().isBefore(startDate)) {
            throw new BusinessException("CONTRACT_PAYMENT_TERM_DATE_INVALID", "付款条款计划日期不能早于合同开始日期");
        }
        if (endDate != null && term.getPlannedDate() != null && term.getPlannedDate().isAfter(endDate)) {
            throw new BusinessException("CONTRACT_PAYMENT_TERM_DATE_INVALID", "付款条款计划日期不能晚于合同结束日期");
        }
        if (startDate != null && term.getActualDate() != null && term.getActualDate().isBefore(startDate)) {
            throw new BusinessException("CONTRACT_PAYMENT_TERM_DATE_INVALID", "付款条款实际日期不能早于合同开始日期");
        }
        if (endDate != null && term.getActualDate() != null && term.getActualDate().isAfter(endDate)) {
            throw new BusinessException("CONTRACT_PAYMENT_TERM_DATE_INVALID", "付款条款实际日期不能晚于合同结束日期");
        }
    }

    private void requireMoneyField(BigDecimal value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        requireNonNegative(value, code, message);
    }

    private void requireNonNegative(BigDecimal value, String code, String message) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(code, message);
        }
    }

    private void validateAmountBreakdown(BigDecimal totalAmount, BigDecimal taxAmount, BigDecimal amountWithoutTax,
                                         String code, String message) {
        if (totalAmount == null || taxAmount == null || amountWithoutTax == null) {
            return;
        }
        if (taxAmount.add(amountWithoutTax).compareTo(totalAmount) != 0) {
            throw new BusinessException(code, message);
        }
    }

}
