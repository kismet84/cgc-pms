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
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import com.cgcpms.common.util.DateTimeUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CtContractChangeService {

    private final CtContractChangeMapper ctContractChangeMapper;
    private final CtContractMapper ctContractMapper;
    private final WorkflowEngine workflowEngine;

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
        return entity;
    }

    @Transactional
    public Long create(CtContractChange change) {
        // 校验合同状态：PERFORMING/APPROVED 才允许创建变更
        CtContract contract = ctContractMapper.selectById(change.getContractId());
        if (contract == null || !contract.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");

        String contractStatus = contract.getContractStatus();
        if (ContractStatusConstants.STATUS_DRAFT.equals(contractStatus)
                || ContractStatusConstants.STATUS_TERMINATED.equals(contractStatus))
            throw new BusinessException("CONTRACT_STATUS_INVALID",
                    "DRAFT 或 TERMINATED 状态的合同禁止创建变更");

        // 自动编号: CC-yyyyMMdd-XXX（含软删除记录查询最大编号，避免 UK 冲突）
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String prefix = "CC-" + today + "-";

        String lastCode = ctContractChangeMapper.selectLastCodeByPrefix(prefix, UserContext.getCurrentTenantId());

        int seq = 1;
        if (lastCode != null && lastCode.startsWith(prefix)) {
            try {
                seq = Integer.parseInt(lastCode.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", lastCode, e);
            }
        }
        change.setChangeCode(prefix + String.format("%03d", seq));

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
        ctContractChangeMapper.insert(change);
        return change.getId();
    }

    @Transactional
    public void update(CtContractChange change) {
        CtContractChange existing = ctContractChangeMapper.selectById(change.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CT_CHANGE_NOT_FOUND", "合同变更不存在");

        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(existing.getApprovalStatus()))
            throw new BusinessException("CT_CHANGE_IN_APPROVAL", "合同变更审批中或已审批，不可编辑");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        ctContractChangeMapper.updateById(change);
    }

    @Transactional
    public void delete(Long id) {
        CtContractChange existing = ctContractChangeMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CT_CHANGE_NOT_FOUND", "合同变更不存在");

        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(existing.getApprovalStatus()))
            throw new BusinessException("CT_CHANGE_IN_APPROVAL", "合同变更审批中或已审批，不可删除");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可删除，请走冲销");

        ctContractChangeMapper.deleteById(id);
    }

    /**
     * 提交合同变更审批。
     */
    @Transactional
    public void submitForApproval(Long id) {
        CtContractChange existing = ctContractChangeMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CT_CHANGE_NOT_FOUND", "合同变更不存在");

        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(existing.getApprovalStatus()))
            throw new BusinessException("CT_CHANGE_ALREADY_SUBMITTED", "合同变更已提交审批，不可重复提交");

        ctContractChangeMapper.update(null, new LambdaUpdateWrapper<CtContractChange>()
                .eq(CtContractChange::getId, id)
                .set(CtContractChange::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVING));

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
    }
}
