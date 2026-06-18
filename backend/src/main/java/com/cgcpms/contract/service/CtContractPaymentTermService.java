package com.cgcpms.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.mapper.CtContractPaymentTermMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CtContractPaymentTermService {

    private final CtContractPaymentTermMapper ctContractPaymentTermMapper;
    private final CtContractMapper ctContractMapper;

    /**
     * Verify parent contract belongs to current tenant and is in DRAFT status (editable).
     */
    private CtContract requireDraftParentContract(Long contractId) {
        CtContract contract = requireParentContract(contractId);
        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(contract.getApprovalStatus()))
            throw new BusinessException("CONTRACT_NOT_EDITABLE", "合同非草稿状态，不可编辑");
        return contract;
    }

    /**
     * Verify parent contract belongs to current tenant.
     */
    private CtContract requireParentContract(Long contractId) {
        CtContract contract = ctContractMapper.selectById(contractId);
        if (contract == null || !contract.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        return contract;
    }

    public List<CtContractPaymentTerm> getByContractId(Long contractId) {
        requireParentContract(contractId);
        LambdaQueryWrapper<CtContractPaymentTerm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CtContractPaymentTerm::getContractId, contractId)
                .orderByAsc(CtContractPaymentTerm::getSortOrder);
        return ctContractPaymentTermMapper.selectList(wrapper);
    }

    @Transactional
    public Long create(CtContractPaymentTerm term) {
        requireParentContract(term.getContractId());
        term.setTenantId(UserContext.getCurrentTenantId());
        ctContractPaymentTermMapper.insert(term);
        return term.getId();
    }

    @Transactional
    public void batchSave(Long contractId, List<CtContractPaymentTerm> newTerms) {
        requireDraftParentContract(contractId);
        LambdaQueryWrapper<CtContractPaymentTerm> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(CtContractPaymentTerm::getContractId, contractId);
        ctContractPaymentTermMapper.delete(deleteWrapper);
        if (!newTerms.isEmpty()) {
            Long tenantId = UserContext.getCurrentTenantId();
            newTerms.forEach(t -> {
                t.setId(null);            // 清空ID，让ASSIGN_ID自动生成新ID，避免与软删除记录主键冲突
                t.setContractId(contractId);
                t.setTenantId(tenantId);
            });
            Db.saveBatch(newTerms);
        }
    }

    @Transactional
    public void update(CtContractPaymentTerm term) {
        requireDraftParentContract(term.getContractId());
        CtContractPaymentTerm existing = ctContractPaymentTermMapper.selectById(term.getId());
        if (existing == null)
            throw new BusinessException("PAYMENT_TERM_NOT_FOUND", "付款条款不存在");
        if (!existing.getContractId().equals(term.getContractId()))
            throw new BusinessException("PAYMENT_TERM_NOT_FOUND", "付款条款不存在");
        term.setTenantId(UserContext.getCurrentTenantId());
        ctContractPaymentTermMapper.updateById(term);
    }

    @Transactional
    public void delete(Long contractId, Long id) {
        requireDraftParentContract(contractId);
        CtContractPaymentTerm existing = ctContractPaymentTermMapper.selectById(id);
        if (existing == null || !existing.getContractId().equals(contractId))
            throw new BusinessException("PAYMENT_TERM_NOT_FOUND", "付款条款不存在");
        ctContractPaymentTermMapper.deleteById(id);
    }
}
