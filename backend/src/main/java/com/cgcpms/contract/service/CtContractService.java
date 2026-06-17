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

    public IPage<CtContractVO> getPage(long pageNo, long pageSize, String contractCode, String contractName,
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
                .map(contract -> contract.getCurrentAmount() != null
                        ? contract.getCurrentAmount()
                        : nullToZero(contract.getContractAmount()))
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
        return toVO(c);
    }

    @Transactional
    public Long create(CtContract contract) {
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String prefix = "CT-" + today + "-";

        LambdaQueryWrapper<CtContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(CtContract::getContractCode, prefix)
                .orderByDesc(CtContract::getContractCode)
                .last("LIMIT 1");
        CtContract last = ctContractMapper.selectOne(wrapper);

        int seq = 1;
        if (last != null && last.getContractCode() != null && last.getContractCode().length() == prefix.length() + 3) {
            try {
                seq = Integer.parseInt(last.getContractCode().substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", last.getContractCode(), e);
            }
        }
        contract.setContractCode(prefix + String.format("%03d", seq));
        contract.setContractStatus("DRAFT");
        contract.setApprovalStatus("DRAFT");

        ctContractMapper.insert(contract);
        return contract.getId();
    }

    @Transactional
    public void update(CtContract contract) {
        CtContract existing = ctContractMapper.selectById(contract.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");

        // 审批中守卫：禁止编辑（超管豁免）
        if (ContractStatusConstants.APPROVAL_APPROVING.equals(existing.getApprovalStatus())
                && !UserContext.hasRole("SUPER_ADMIN"))
            throw new BusinessException("CONTRACT_IN_APPROVAL", "合同审批中，不可编辑");

        // 禁止通过 update 接口覆盖审批状态
        contract.setApprovalStatus(existing.getApprovalStatus());

        ctContractMapper.updateById(contract);
    }

    /**
     * 提交合同审批。
     */
    @Transactional
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
    @Transactional
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
    @Transactional
    public Long compositeSave(ContractSaveRequest request) {
        CtContract contract = request.getContract();

        if (contract.getId() == null) {
            // ── 新建 ──
            String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
            String prefix = "CT-" + today + "-";

            LambdaQueryWrapper<CtContract> wrapper = new LambdaQueryWrapper<>();
            wrapper.likeRight(CtContract::getContractCode, prefix)
                    .orderByDesc(CtContract::getContractCode)
                    .last("LIMIT 1");
            CtContract last = ctContractMapper.selectOne(wrapper);

            int seq = 1;
            if (last != null && last.getContractCode() != null
                    && last.getContractCode().length() == prefix.length() + 3) {
                try {
                    seq = Integer.parseInt(last.getContractCode().substring(prefix.length())) + 1;
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse sequence number: {}", last.getContractCode(), e);
                }
            }
            contract.setContractCode(prefix + String.format("%03d", seq));
            contract.setContractStatus("DRAFT");
            contract.setApprovalStatus("DRAFT");

            ctContractMapper.insert(contract);
        } else {
            // ── 更新 ──
            CtContract existing = ctContractMapper.selectById(contract.getId());
            if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
                throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");

            if (ContractStatusConstants.APPROVAL_APPROVING.equals(existing.getApprovalStatus())
                    && !UserContext.hasRole("SUPER_ADMIN"))
                throw new BusinessException("CONTRACT_IN_APPROVAL", "合同审批中，不可编辑");

            contract.setApprovalStatus(existing.getApprovalStatus());
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

        // ── 可选：立即提交审批（仅草稿状态可提交）──
        if (Boolean.TRUE.equals(request.getSubmitForApproval())
                && ContractStatusConstants.APPROVAL_DRAFT.equals(contract.getApprovalStatus())) {
            submitForApproval(contractId);
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
        vo.setWarrantyRate(c.getWarrantyRate() != null ? c.getWarrantyRate().toPlainString() : null);
        vo.setWarrantyAmount(c.getWarrantyAmount() != null ? c.getWarrantyAmount().toPlainString() : null);
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
