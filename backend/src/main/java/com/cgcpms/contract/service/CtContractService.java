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
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.vo.ContractApprovalRecordVO;
import com.cgcpms.contract.vo.CtContractVO;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfRecord;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfRecordMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final CtContractMapper ctContractMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final CtContractItemService itemService;
    private final CtContractPaymentTermService paymentTermService;
    private final WorkflowEngine workflowEngine;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfRecordMapper wfRecordMapper;
    private final CodeGenerationService codeGenerationService;
    private final ProjectAccessChecker projectAccessChecker;

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
                : pmProjectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> partyNames = allPartyIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectBatchIds(allPartyIds).stream()
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
        validateContractParties(contract);
        String code = codeGenerationService.nextCode(
                ctContractMapper,
                CtContract::getContractCode,
                "CT-",
                UserContext.getCurrentTenantId(),
                true  // includeDeleted 避免软删除 UK 冲突
        );
        contract.setContractCode(code);
        contract.setContractStatus("DRAFT");
        contract.setApprovalStatus("DRAFT");
        contract.setTenantId(UserContext.getCurrentTenantId());

        ctContractMapper.insert(contract);
        return contract.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CtContract contract) {
        CtContract existing = ctContractMapper.selectById(contract.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");

        // 编辑守卫：只允许 DRAFT 状态编辑
        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(existing.getApprovalStatus()))
            throw new BusinessException("CONTRACT_NOT_EDITABLE", "合同非草稿状态，不可编辑");

        validateContractParties(contract);

        // 使用字段白名单更新，禁止通过 update 接口覆盖受保护字段
        ctContractMapper.update(null,
                new LambdaUpdateWrapper<CtContract>()
                        .eq(CtContract::getId, contract.getId())
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

        // 更新审批状态为审批中
        LambdaUpdateWrapper<CtContract> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(CtContract::getId, contractId)
                .set(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVING);
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
        validateContractParties(contract);

        if (contract.getId() == null) {
            // ── 新建 ──
            String code = codeGenerationService.nextCode(
                    ctContractMapper,
                    CtContract::getContractCode,
                    "CT-",
                    UserContext.getCurrentTenantId(),
                    true  // includeDeleted 避免软删除 UK 冲突
            );
            contract.setContractCode(code);
            contract.setContractStatus("DRAFT");
            contract.setApprovalStatus("DRAFT");
            contract.setTenantId(UserContext.getCurrentTenantId());

            ctContractMapper.insert(contract);
        } else {
            // ── 更新 ──
            CtContract existing = ctContractMapper.selectById(contract.getId());
            if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
                throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");

            // 编辑守卫：只允许 DRAFT 状态编辑
            if (!ContractStatusConstants.APPROVAL_DRAFT.equals(existing.getApprovalStatus()))
                throw new BusinessException("CONTRACT_NOT_EDITABLE", "合同非草稿状态，不可编辑");

            // 禁止通过更新接口覆盖受保护字段
            contract.setApprovalStatus(existing.getApprovalStatus());
            contract.setTenantId(existing.getTenantId());
            contract.setPaidAmount(existing.getPaidAmount());
            contract.setCreatedBy(existing.getCreatedBy());
            contract.setCreatedAt(existing.getCreatedAt());
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

    private void validateContractParties(CtContract contract) {
        if (contract == null || contract.getPartyAId() == null || contract.getPartyBId() == null) {
            throw new BusinessException("CONTRACT_PARTY_REQUIRED", "合同甲方和乙方不能为空");
        }
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

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
