package com.cgcpms.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.dto.ContractSaveRequest;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.vo.ContractApprovalRecordVO;
import com.cgcpms.contract.vo.ContractPerformanceReportVO;
import com.cgcpms.contract.vo.CtContractVO;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.system.dict.service.SysDictDataService;
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
import java.time.LocalDate;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.common.util.CodeGenerationService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CtContractService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;

    private final CtContractMapper ctContractMapper;
    private final CtContractChangeMapper ctContractChangeMapper;
    private final PayRecordMapper payRecordMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final CtContractItemService itemService;
    private final CtContractPaymentTermService paymentTermService;
    private final WorkflowEngine workflowEngine;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfRecordMapper wfRecordMapper;
    private final CodeGenerationService codeGenerationService;
    private final ProjectAccessChecker projectAccessChecker;
    private final SysDictDataService sysDictDataService;

    public IPage<CtContractVO> getPage(long pageNo, long pageSize, String keyword,
                                       String contractCode, String contractName,
                                       String contractType, String contractStatus, String approvalStatus,
                                       Long projectId, Long partyAId, Long partyBId) {
        LambdaQueryWrapper<CtContract> wrapper = new LambdaQueryWrapper<>();
        // keyword 全局搜索：匹配合同编号、合同名称、合同类型、甲方名称、乙方名称等字段
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w ->
                w.like(CtContract::getContractCode, keyword)
                    .or().like(CtContract::getContractName, keyword)
                    .or().like(CtContract::getContractType, keyword)
            );
        }
        if (StringUtils.hasText(contractCode)) wrapper.like(CtContract::getContractCode, contractCode);
        if (StringUtils.hasText(contractName)) wrapper.like(CtContract::getContractName, contractName);
        if (StringUtils.hasText(contractType)) wrapper.eq(CtContract::getContractType, contractType);
        if (StringUtils.hasText(contractStatus)) wrapper.eq(CtContract::getContractStatus, contractStatus);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(CtContract::getApprovalStatus, approvalStatus);
        if (projectId != null) wrapper.eq(CtContract::getProjectId, projectId);
        if (partyAId != null) wrapper.eq(CtContract::getPartyAId, partyAId);
        if (partyBId != null) wrapper.eq(CtContract::getPartyBId, partyBId);
        wrapper.eq(CtContract::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByDesc(CtContract::getCreatedAt);

        Page<CtContract> page = ctContractMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        // Batch-prefetch related project/partner names to avoid N+1 queries.
        List<CtContract> records = page.getRecords();
        Set<Long> projectIds = records.stream()
                .map(CtContract::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
                Set<Long> partyAIds = records.stream()
                .map(CtContract::getPartyAId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> partyBIds = records.stream()
                .map(CtContract::getPartyBId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allPartyIds = new java.util.HashSet<>(partyAIds);
        allPartyIds.addAll(partyBIds);

        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectByIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> partyNames = allPartyIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectByIds(allPartyIds).stream()
                        .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));

        return page.convert(c -> toVO(c, projectNames, partyNames));
    }

    public Map<String, Object> getKpi(String contractCode, String contractName,
                                      String contractType, String contractStatus, String approvalStatus,
                                      Long projectId, Long partyAId, Long partyBId) {
        LambdaQueryWrapper<CtContract> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(contractCode)) wrapper.like(CtContract::getContractCode, contractCode);
        if (StringUtils.hasText(contractName)) wrapper.like(CtContract::getContractName, contractName);
        if (StringUtils.hasText(contractType)) wrapper.eq(CtContract::getContractType, contractType);
        if (StringUtils.hasText(contractStatus)) wrapper.eq(CtContract::getContractStatus, contractStatus);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(CtContract::getApprovalStatus, approvalStatus);
        if (projectId != null) wrapper.eq(CtContract::getProjectId, projectId);
        if (partyAId != null) wrapper.eq(CtContract::getPartyAId, partyAId);
        if (partyBId != null) wrapper.eq(CtContract::getPartyBId, partyBId);
        wrapper.eq(CtContract::getTenantId, UserContext.getCurrentTenantId());

        List<CtContract> contracts = ctContractMapper.selectList(wrapper);
        BigDecimal totalAmount = contracts.stream()
                .map(contract -> {
                    BigDecimal current = contract.getCurrentAmount();
                    // currentAmount 默认值为 0（非 null），无法区分"未设"和"真的为 0"
                    // 当 currentAmount 为 0 或 null 时回退到 contractAmount
                    if (current != null && current.compareTo(BigDecimal.ZERO) != 0) {
                        return current;
                    }
                    return nullToZero(contract.getContractAmount());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = contracts.stream()
                .map(contract -> nullToZero(contract.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long overdueCount = contracts.stream()
                .filter(contract -> contract.getEndDate() != null && contract.getEndDate().isBefore(LocalDate.now()))
                .filter(contract -> !"SETTLED".equals(contract.getContractStatus()))
                .count();

        return Map.of(
                "totalCount", (long) contracts.size(),
                "totalAmount", totalAmount.toPlainString(),
                "paidAmount", paidAmount.toPlainString(),
                "unpaidAmount", totalAmount.subtract(paidAmount).toPlainString(),
                "overdueCount", overdueCount);
    }

    public ContractPerformanceReportVO getPerformanceReport(Long projectId) {
        Long tenantId = UserContext.getCurrentTenantId();
        LambdaQueryWrapper<CtContract> wrapper = new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getTenantId, tenantId);
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, "查看合同履约报表");
            wrapper.eq(CtContract::getProjectId, projectId);
        }
        List<CtContract> contracts = ctContractMapper.selectList(wrapper);
        List<Long> contractIds = contracts.stream().map(CtContract::getId).toList();

        Map<Long, BigDecimal> changeByContract = contractIds.isEmpty() ? Map.of()
                : ctContractChangeMapper.selectList(new LambdaQueryWrapper<CtContractChange>()
                        .eq(CtContractChange::getTenantId, tenantId)
                        .in(CtContractChange::getContractId, contractIds)
                        .eq(CtContractChange::getApprovalStatus, "APPROVED"))
                .stream()
                .collect(Collectors.groupingBy(CtContractChange::getContractId,
                        Collectors.mapping(c -> nullToZero(c.getChangeAmount()),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        Map<Long, BigDecimal> paidByContract = contractIds.isEmpty() ? Map.of()
                : payRecordMapper.selectList(new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getTenantId, tenantId)
                        .in(PayRecord::getContractId, contractIds)
                        .eq(PayRecord::getPayStatus, "SUCCESS"))
                .stream()
                .collect(Collectors.groupingBy(PayRecord::getContractId,
                        Collectors.mapping(p -> nullToZero(p.getPayAmount()),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        ContractPerformanceReportVO report = new ContractPerformanceReportVO();
        List<ContractPerformanceReportVO.Row> rows = contracts.stream()
                .map(contract -> toPerformanceRow(contract, changeByContract, paidByContract))
                .toList();
        report.setRows(rows);
        BigDecimal totalContractAmount = contracts.stream()
                .map(c -> nullToZero(c.getContractAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalChangeAmount = changeByContract.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaidAmount = paidByContract.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        report.setTotalContractAmount(totalContractAmount.toPlainString());
        report.setTotalChangeAmount(totalChangeAmount.toPlainString());
        report.setTotalPaidAmount(totalPaidAmount.toPlainString());
        report.setPaymentProgress(formatRatio(totalPaidAmount, totalContractAmount.add(totalChangeAmount)));
        return report;
    }

    public CtContractVO getById(Long id) {
        CtContract c = ctContractMapper.selectById(id);
        if (c == null || !c.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        if (c.getProjectId() != null) {
            projectAccessChecker.checkAccess(c.getProjectId(), "查看合同详情");
        }
        return toVO(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CtContract contract) {
        normalizeContractType(contract);
        validateContractReferences(contract);
        contract.setContractStatus("DRAFT");
        contract.setApprovalStatus("DRAFT");
        contract.setTenantId(UserContext.getCurrentTenantId());

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
        CtContract existing = ctContractMapper.selectById(contract.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");

        // 编辑守卫：只允许 DRAFT 状态编辑
        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(existing.getApprovalStatus()))
            throw new BusinessException("CONTRACT_NOT_EDITABLE", "合同非草稿状态，不可编辑");

        normalizeContractType(contract);
        validateContractReferences(contract);

        // 校验合同状态：仅允许在有效状态集合内修改
        if (contract.getContractStatus() != null
                && !Set.of(ContractStatusConstants.STATUS_DRAFT,
                           ContractStatusConstants.STATUS_PERFORMING,
                           ContractStatusConstants.STATUS_SETTLED,
                           ContractStatusConstants.STATUS_TERMINATED)
                        .contains(contract.getContractStatus())) {
            throw new BusinessException("CONTRACT_STATUS_INVALID", "合同状态不合法");
        }

        // 乐观锁：携带现有版本号，更新时自动校验
        contract.setVersion(existing.getVersion());

        // 使用字段白名单更新，禁止通过 update 接口覆盖受保护字段
        ctContractMapper.update(null,
                new LambdaUpdateWrapper<CtContract>()
                        .eq(CtContract::getId, contract.getId())
                        .eq(CtContract::getVersion, existing.getVersion())
                        .set(CtContract::getContractName, contract.getContractName())
                        .set(CtContract::getContractType, contract.getContractType())
                        .set(CtContract::getProjectId, contract.getProjectId())
                        .set(CtContract::getOrgId, contract.getOrgId())
                        .set(CtContract::getPartyAId, contract.getPartyAId())
                        .set(CtContract::getPartyBId, contract.getPartyBId())
                        .set(CtContract::getContractAmount, contract.getContractAmount())
                        .set(CtContract::getCurrentAmount, contract.getCurrentAmount())
                        .set(CtContract::getTaxRate, contract.getTaxRate())
                        .set(CtContract::getTaxAmount, contract.getTaxAmount())
                        .set(CtContract::getAmountWithoutTax, contract.getAmountWithoutTax())
                        .set(CtContract::getSignedDate, contract.getSignedDate())
                        .set(CtContract::getStartDate, contract.getStartDate())
                        .set(CtContract::getEndDate, contract.getEndDate())
                        .set(CtContract::getPaymentMethod, contract.getPaymentMethod())
                        .set(CtContract::getSettlementMethod, contract.getSettlementMethod())
                        .set(CtContract::getContractStatus, contract.getContractStatus())
                        .set(CtContract::getRemark, contract.getRemark())
                        .set(CtContract::getVersion, existing.getVersion() + 1)
        );
    }

    /**
     * 提交合同审批。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long contractId) {
        CtContract contract = ctContractMapper.selectById(contractId);
        if (contract == null || !contract.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");

        // 只允许草稿状态提交
        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(contract.getApprovalStatus()))
            throw new BusinessException("CONTRACT_ALREADY_SUBMITTED", "合同已提交审批，不可重复提交");

        // 必须有合同编号
        if (contract.getContractCode() == null || contract.getContractCode().isBlank())
            throw new BusinessException("CONTRACT_NO_CODE", "合同编号不能为空，无法提交审批");

        validatePurchaseSupplierAdmission(contract);

        // 更新审批状态为审批中（携带版本号乐观锁）
        LambdaUpdateWrapper<CtContract> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(CtContract::getId, contractId)
                .eq(CtContract::getVersion, contract.getVersion())
                .set(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVING)
                .set(CtContract::getVersion, contract.getVersion() + 1);
        ctContractMapper.update(null, updateWrapper);

        // 调用审批引擎
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        workflowEngine.submit(userId, username, tenantId,
                ContractStatusConstants.BUSINESS_TYPE_CONTRACT_APPROVAL,
                contractId,
                contract.getContractName(),
                contract.getContractAmount(),
                contract.getProjectId(),
                contractId,
                null, null, null);
    }

    /**
     * 删除合同（软删除，仅限 DRAFT 状态）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CtContract existing = ctContractMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");

        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(existing.getApprovalStatus())
                && !UserContext.hasRole("SUPER_ADMIN"))
            throw new BusinessException("CONTRACT_IN_APPROVAL", "合同审批中或已审批，不可删除");

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

        if (contract.getId() == null) {
            // ── 新建 ──
            normalizeContractType(contract);
            validateContractReferences(contract);
            contract.setContractStatus("DRAFT");
            contract.setApprovalStatus("DRAFT");
            contract.setTenantId(UserContext.getCurrentTenantId());

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
            CtContract existing = ctContractMapper.selectById(contract.getId());
            if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
                throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");

            // 编辑守卫：只允许 DRAFT 状态编辑
            if (!ContractStatusConstants.APPROVAL_DRAFT.equals(existing.getApprovalStatus()))
                throw new BusinessException("CONTRACT_NOT_EDITABLE", "合同非草稿状态，不可编辑");

            normalizeContractType(contract);
            validateContractReferences(contract);

            // 禁止通过更新接口覆盖受保护字段
            contract.setApprovalStatus(existing.getApprovalStatus());
            contract.setTenantId(existing.getTenantId());
            contract.setPaidAmount(existing.getPaidAmount());
            contract.setCreatedBy(existing.getCreatedBy());
            contract.setCreatedAt(existing.getCreatedAt());
            // @Version 不会在 selectById 时自动填充，需手动复制以确保 MyBatis-Plus
            // 在 UPDATE WHERE 中携带正确版本号实现乐观锁
            contract.setVersion(existing.getVersion());
            ctContractMapper.updateById(contract);
        }

        Long contractId = contract.getId();

        // ── 批量保存明细项（delete-then-insert）──
        List<CtContractItem> items = request.getItems();
        if (items != null) {
            itemService.batchSave(contractId, items);
        }

        // ── 批量保存付款条款（delete-then-insert）──
        List<CtContractPaymentTerm> terms = request.getPaymentTerms();
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
        Long tenantId = UserContext.getCurrentTenantId();
        // 1. 查 wf_instance WHERE businessType=CONTRACT_APPROVAL AND businessId=contractId AND tenantId=?
        LambdaQueryWrapper<WfInstance> instQw = new LambdaQueryWrapper<>();
        instQw.eq(WfInstance::getBusinessType, ContractStatusConstants.BUSINESS_TYPE_CONTRACT_APPROVAL)
                .eq(WfInstance::getBusinessId, contractId)
                .eq(WfInstance::getTenantId, tenantId);
        WfInstance instance = wfInstanceMapper.selectOne(instQw);
        if (instance == null) return List.of();

        // 2. 查 wf_record WHERE instanceId=instance.id AND tenantId=? ORDER BY createdAt ASC
        LambdaQueryWrapper<WfRecord> recQw = new LambdaQueryWrapper<>();
        recQw.eq(WfRecord::getInstanceId, instance.getId())
                .eq(WfRecord::getTenantId, tenantId)
                .orderByAsc(WfRecord::getCreatedAt);
        List<WfRecord> records = wfRecordMapper.selectList(recQw);

        // 3. 转 VO
        return records.stream().map(r -> {
            ContractApprovalRecordVO vo = new ContractApprovalRecordVO();
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

    private void validateContractReferences(CtContract contract) {
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
        MdPartner partyA = mdPartnerMapper.selectById(contract.getPartyAId());
        if (partyA == null || !java.util.Objects.equals(partyA.getTenantId(), tenantId)) {
            throw new BusinessException("CONTRACT_PARTY_A_NOT_FOUND", "合同甲方不存在");
        }
        MdPartner partyB = mdPartnerMapper.selectById(contract.getPartyBId());
        if (partyB == null || !java.util.Objects.equals(partyB.getTenantId(), tenantId)) {
            throw new BusinessException("CONTRACT_PARTY_B_NOT_FOUND", "合同乙方不存在");
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

    private CtContractVO toVO(CtContract c) {
        // Single-record variant: fetch project/partner individually (for getById).
        CtContractVO vo = buildBaseVO(c);
        if (c.getProjectId() != null) {
            PmProject project = pmProjectMapper.selectById(c.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
        }
                if (c.getPartyAId() != null) {
            MdPartner partyA = mdPartnerMapper.selectById(c.getPartyAId());
            if (partyA != null) vo.setPartyAName(partyA.getPartnerName());
        }
        if (c.getPartyBId() != null) {
            MdPartner partyB = mdPartnerMapper.selectById(c.getPartyBId());
            if (partyB != null) vo.setPartyBName(partyB.getPartnerName());
        }
        return vo;
    }

    private CtContractVO toVO(CtContract c, Map<Long, String> projectNames, Map<Long, String> partyNames) {
        // Batch-friendly variant: use pre-fetched maps to avoid N+1.
        CtContractVO vo = buildBaseVO(c);
        if (c.getProjectId() != null) {
            vo.setProjectName(projectNames.get(c.getProjectId()));
        }
                if (c.getPartyAId() != null) {
            vo.setPartyAName(partyNames.get(c.getPartyAId()));
        }
        if (c.getPartyBId() != null) {
            vo.setPartyBName(partyNames.get(c.getPartyBId()));
        }
        return vo;
    }

    private CtContractVO buildBaseVO(CtContract c) {
        CtContractVO vo = new CtContractVO();
        vo.setId(c.getId() != null ? c.getId().toString() : null);
        vo.setTenantId(c.getTenantId() != null ? c.getTenantId().toString() : null);
        vo.setOrgId(c.getOrgId() != null ? c.getOrgId().toString() : null);
        vo.setProjectId(c.getProjectId() != null ? c.getProjectId().toString() : null);
        
        vo.setContractCode(c.getContractCode());
        vo.setContractName(c.getContractName());
        vo.setContractType(c.getContractType());
        vo.setPartyAId(c.getPartyAId() != null ? c.getPartyAId().toString() : null);
        vo.setPartyBId(c.getPartyBId() != null ? c.getPartyBId().toString() : null);
        vo.setContractAmount(c.getContractAmount() != null ? c.getContractAmount().toPlainString() : null);
        vo.setCurrentAmount(c.getCurrentAmount() != null ? c.getCurrentAmount().toPlainString() : null);
        vo.setTaxRate(c.getTaxRate() != null ? c.getTaxRate().toPlainString() : null);
        vo.setTaxAmount(c.getTaxAmount() != null ? c.getTaxAmount().toPlainString() : null);
        vo.setAmountWithoutTax(c.getAmountWithoutTax() != null ? c.getAmountWithoutTax().toPlainString() : null);
        vo.setSignedDate(c.getSignedDate() != null ? DateTimeUtils.DATE_FMT.format(c.getSignedDate()) : null);
        vo.setStartDate(c.getStartDate() != null ? DateTimeUtils.DATE_FMT.format(c.getStartDate()) : null);
        vo.setEndDate(c.getEndDate() != null ? DateTimeUtils.DATE_FMT.format(c.getEndDate()) : null);
        vo.setPaymentMethod(c.getPaymentMethod());
        vo.setSettlementMethod(c.getSettlementMethod());
        vo.setPaidAmount(c.getPaidAmount() != null ? c.getPaidAmount().toPlainString() : null);
        vo.setSettlementAmount(c.getSettlementAmount() != null ? c.getSettlementAmount().toPlainString() : null);
        vo.setContractStatus(c.getContractStatus());
        vo.setApprovalStatus(c.getApprovalStatus());
        vo.setCreatedBy(c.getCreatedBy() != null ? c.getCreatedBy().toString() : null);
        vo.setCreatedAt(c.getCreatedAt() != null ? DateTimeUtils.DTF.format(c.getCreatedAt()) : null);
        vo.setUpdatedAt(c.getUpdatedAt() != null ? DateTimeUtils.DTF.format(c.getUpdatedAt()) : null);
        vo.setRemark(c.getRemark());
        return vo;
    }

    private ContractPerformanceReportVO.Row toPerformanceRow(CtContract contract,
                                                             Map<Long, BigDecimal> changeByContract,
                                                             Map<Long, BigDecimal> paidByContract) {
        ContractPerformanceReportVO.Row row = new ContractPerformanceReportVO.Row();
        BigDecimal contractAmount = nullToZero(contract.getContractAmount());
        BigDecimal changeAmount = changeByContract.getOrDefault(contract.getId(), BigDecimal.ZERO);
        BigDecimal paidAmount = paidByContract.getOrDefault(contract.getId(), BigDecimal.ZERO);
        BigDecimal currentAmount = contract.getCurrentAmount() != null && contract.getCurrentAmount().compareTo(BigDecimal.ZERO) != 0
                ? contract.getCurrentAmount()
                : contractAmount.add(changeAmount);

        row.setContractId(String.valueOf(contract.getId()));
        row.setContractCode(contract.getContractCode());
        row.setContractName(contract.getContractName());
        row.setContractStatus(contract.getContractStatus());
        row.setContractAmount(contractAmount.toPlainString());
        row.setChangeAmount(changeAmount.toPlainString());
        row.setPaidAmount(paidAmount.toPlainString());
        row.setPaymentProgress(formatRatio(paidAmount, currentAmount));
        return row;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String formatRatio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return "0.0000";
        }
        return numerator.divide(denominator, 4, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
