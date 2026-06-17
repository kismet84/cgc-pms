package com.cgcpms.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.mapper.CtContractPaymentTermMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CtContractPaymentTermService {

    private final CtContractPaymentTermMapper ctContractPaymentTermMapper;

    public List<CtContractPaymentTerm> getByContractId(Long contractId) {
        LambdaQueryWrapper<CtContractPaymentTerm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CtContractPaymentTerm::getContractId, contractId)
                .orderByAsc(CtContractPaymentTerm::getSortOrder);
        return ctContractPaymentTermMapper.selectList(wrapper);
    }

    @Transactional
    public Long create(CtContractPaymentTerm term) {
        ctContractPaymentTermMapper.insert(term);
        return term.getId();
    }

    @Transactional
    public void batchSave(Long contractId, List<CtContractPaymentTerm> newTerms) {
        LambdaQueryWrapper<CtContractPaymentTerm> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(CtContractPaymentTerm::getContractId, contractId);
        ctContractPaymentTermMapper.delete(deleteWrapper);
        if (!newTerms.isEmpty()) {
            newTerms.forEach(t -> {
                t.setId(null);            // 清空ID，让ASSIGN_ID自动生成新ID，避免与软删除记录主键冲突
                t.setContractId(contractId);
            });
            Db.saveBatch(newTerms);
        }
    }

    @Transactional
    public void update(CtContractPaymentTerm term) {
        CtContractPaymentTerm existing = ctContractPaymentTermMapper.selectById(term.getId());
        if (existing == null)
            throw new BusinessException("PAYMENT_TERM_NOT_FOUND", "付款条款不存在");
        if (!existing.getContractId().equals(term.getContractId()))
            throw new BusinessException("PAYMENT_TERM_NOT_FOUND", "付款条款不存在");
        ctContractPaymentTermMapper.updateById(term);
    }

    @Transactional
    public void delete(Long contractId, Long id) {
        CtContractPaymentTerm existing = ctContractPaymentTermMapper.selectById(id);
        if (existing == null || !existing.getContractId().equals(contractId))
            throw new BusinessException("PAYMENT_TERM_NOT_FOUND", "付款条款不存在");
        ctContractPaymentTermMapper.deleteById(id);
    }
}
