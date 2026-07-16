package com.cgcpms.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.variation.entity.VarOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.cgcpms.common.util.DateTimeUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CtContractChangeService {

    private final CtContractChangeMapper ctContractChangeMapper;
    private final CtContractMapper ctContractMapper;
    private final WorkflowEngine workflowEngine;
    private final ProjectAccessChecker projectAccessChecker;
    private final BusinessMatterRegistryService businessMatterRegistryService;

    public IPage<CtContractChange> getPage(long pageNo, long pageSize, Long projectId, Long contractId,
                                           String changeType, String approvalStatus, String changeCode) {
        LambdaQueryWrapper<CtContractChange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CtContractChange::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) wrapper.eq(CtContractChange::getProjectId, projectId);
        if (contractId != null) wrapper.eq(CtContractChange::getContractId, contractId);
        if (StringUtils.hasText(changeType)) wrapper.eq(CtContractChange::getChangeType, changeType);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(CtContractChange::getApprovalStatus, approvalStatus);
        if (StringUtils.hasText(changeCode)) wrapper.like(CtContractChange::getChangeCode, changeCode);
        wrapper.orderByDesc(CtContractChange::getCreatedTime);

        return ctContractChangeMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    public CtContractChange getById(Long id) {
        CtContractChange entity = ctContractChangeMapper.selectById(id);
        if (entity == null || !entity.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CT_CHANGE_NOT_FOUND", "合同变更不存在");
        checkProjectAccess(entity.getProjectId(), "查看合同变更");
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CtContractChange change) {
        // 校验合同状态：PERFORMING/APPROVED 才允许创建变更
        CtContract contract = ctContractMapper.selectById(change.getContractId());
        if (contract == null || !contract.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        checkProjectAccess(change.getProjectId(), "创建合同变更");
        if (!java.util.Objects.equals(contract.getProjectId(), change.getProjectId()))
            throw new BusinessException("CONTRACT_PROJECT_MISMATCH", "合同不属于当前项目");

        String contractStatus = contract.getContractStatus();
        if (ContractStatusConstants.STATUS_DRAFT.equals(contractStatus)
                || ContractStatusConstants.STATUS_TERMINATED.equals(contractStatus))
            throw new BusinessException("CONTRACT_STATUS_INVALID",
                    "DRAFT 或 TERMINATED 状态的合同禁止创建变更");

        // 自动编号: CC-yyyyMMdd-XXX（含软删除记录查询最大编号，避免 UK 冲突）
        change.setChangeCode(nextChangeCode());

        // 默认值
        if (change.getApprovalStatus() == null || change.getApprovalStatus().isBlank()) {
            change.setApprovalStatus(ContractStatusConstants.APPROVAL_DRAFT);
        }
        if (change.getEffectiveFlag() == null) {
            change.setEffectiveFlag(0);
        }
        if (change.getCostGeneratedFlag() == null) {
            change.setCostGeneratedFlag(0);
        }

        change.setTenantId(UserContext.getCurrentTenantId());
        change.setBusinessMatterKey(businessMatterRegistryService.normalize(change.getBusinessMatterKey()));
        ctContractChangeMapper.insert(change);
        businessMatterRegistryService.register(BusinessMatterRegistryService.SOURCE_CONTRACT_CHANGE,
                change.getId(), change.getProjectId(), change.getContractId(), change.getBusinessMatterKey());
        return change.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CtContractChange change) {
        CtContractChange existing = ctContractChangeMapper.selectById(change.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CT_CHANGE_NOT_FOUND", "合同变更不存在");
        checkProjectAccess(existing.getProjectId(), "编辑合同变更");

        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(existing.getApprovalStatus()))
            throw new BusinessException("CT_CHANGE_IN_APPROVAL", "合同变更审批中或已审批，不可编辑");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        if (change.getBusinessMatterKey() != null) {
            businessMatterRegistryService.replace(BusinessMatterRegistryService.SOURCE_CONTRACT_CHANGE,
                    existing.getId(), existing.getProjectId(), existing.getContractId(),
                    existing.getBusinessMatterKey(), change.getBusinessMatterKey());
            change.setBusinessMatterKey(businessMatterRegistryService.normalize(change.getBusinessMatterKey()));
        }
        ctContractChangeMapper.updateById(change);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CtContractChange existing = ctContractChangeMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CT_CHANGE_NOT_FOUND", "合同变更不存在");
        checkProjectAccess(existing.getProjectId(), "删除合同变更");

        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(existing.getApprovalStatus()))
            throw new BusinessException("CT_CHANGE_IN_APPROVAL", "合同变更审批中或已审批，不可删除");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可删除，请走冲销");

        ctContractChangeMapper.deleteById(id);
        businessMatterRegistryService.release(BusinessMatterRegistryService.SOURCE_CONTRACT_CHANGE,
                id, "合同变更草稿删除");
    }

    /**
     * 提交合同变更审批。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long id) {
        CtContractChange existing = ctContractChangeMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CT_CHANGE_NOT_FOUND", "合同变更不存在");
        checkProjectAccess(existing.getProjectId(), "提交合同变更审批");

        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(existing.getApprovalStatus()))
            throw new BusinessException("CT_CHANGE_ALREADY_SUBMITTED", "合同变更已提交审批，不可重复提交");

        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        workflowEngine.submit(userId, username, tenantId,
                "CT_CHANGE",
                id,
                existing.getChangeCode(),
                existing.getChangeAmount(),
                existing.getProjectId(),
                existing.getContractId(),
                null, null, null);

        ctContractChangeMapper.update(null, new LambdaUpdateWrapper<CtContractChange>()
                .eq(CtContractChange::getId, id)
                .set(CtContractChange::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVING));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createFromVariationAndSubmit(VarOrder order, BigDecimal confirmedAmount) {
        if (order == null || order.getId() == null)
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");
        BigDecimal amount = confirmedAmount == null ? BigDecimal.ZERO
                : confirmedAmount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0)
            throw new BusinessException("VARIATION_OWNER_CONFIRMED_AMOUNT_INVALID", "业主核定金额必须大于0");

        CtContractChange existing = ctContractChangeMapper.selectOne(new LambdaQueryWrapper<CtContractChange>()
                .eq(CtContractChange::getTenantId, UserContext.getCurrentTenantId())
                .eq(CtContractChange::getSourceVarOrderId, order.getId()));
        if (existing != null) return existing.getId();

        CtContract contract = ctContractMapper.selectByIdForUpdate(order.getContractId(), UserContext.getCurrentTenantId());
        if (contract == null || !java.util.Objects.equals(contract.getProjectId(), order.getProjectId()))
            throw new BusinessException("CONTRACT_PROJECT_MISMATCH", "合同不存在或不属于当前项目");
        if (!"MAIN".equals(contract.getContractType())
                || !ContractStatusConstants.APPROVAL_APPROVED.equals(contract.getApprovalStatus())
                || !ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus()))
            throw new BusinessException("OWNER_CONTRACT_NOT_PERFORMING", "只有已审批且履约中的业主主合同可以生成正式变更");

        BigDecimal before = contract.getCurrentAmount() == null ? contract.getContractAmount() : contract.getCurrentAmount();
        CtContractChange change = new CtContractChange();
        change.setTenantId(UserContext.getCurrentTenantId());
        change.setProjectId(order.getProjectId());
        change.setContractId(order.getContractId());
        change.setSourceVarOrderId(order.getId());
        change.setChangeCode(nextChangeCode());
        change.setChangeName("业主核定-" + order.getVarCode() + "-" + order.getVarName());
        change.setChangeType("AMOUNT");
        change.setBeforeAmount(before);
        change.setChangeAmount(amount);
        change.setAfterAmount(before.add(amount));
        change.setReason("由变更签证业主核定自动生成，来源=" + order.getVarCode());
        change.setApprovalStatus(ContractStatusConstants.APPROVAL_DRAFT);
        change.setEffectiveFlag(0);
        change.setCostGeneratedFlag(0);
        change.setRemark("系统自动生成，禁止脱离来源签证修改");
        ctContractChangeMapper.insert(change);

        workflowEngine.submit(UserContext.getCurrentUserId(), UserContext.getCurrentUsername(),
                UserContext.getCurrentTenantId(), "CT_CHANGE", change.getId(), change.getChangeCode(), amount,
                change.getProjectId(), change.getContractId(), null, null, null);
        ctContractChangeMapper.update(null, new LambdaUpdateWrapper<CtContractChange>()
                .eq(CtContractChange::getId, change.getId())
                .eq(CtContractChange::getApprovalStatus, ContractStatusConstants.APPROVAL_DRAFT)
                .set(CtContractChange::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVING));
        return change.getId();
    }

    private String nextChangeCode() {
        String prefix = "CC-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
        String lastCode = ctContractChangeMapper.selectLastCodeByPrefix(prefix, UserContext.getCurrentTenantId());
        int seq = 1;
        if (lastCode != null && lastCode.startsWith(prefix)) {
            try {
                seq = Integer.parseInt(lastCode.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", lastCode, e);
            }
        }
        return prefix + String.format("%03d", seq);
    }

    private void checkProjectAccess(Long projectId, String action) {
        if (projectId == null) {
            throw new BusinessException("PROJECT_REQUIRED", "合同变更缺少项目关系");
        }
        projectAccessChecker.checkAccess(projectId, action);
    }
}
