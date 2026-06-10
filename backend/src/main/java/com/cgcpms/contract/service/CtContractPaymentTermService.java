package com.cgcpms.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        for (CtContractPaymentTerm term : newTerms) {
            ctContractPaymentTermMapper.insert(term);
        }
    }

    @Transactional
    public void update(CtContractPaymentTerm term) {
        if (ctContractPaymentTermMapper.selectById(term.getId()) == null)
            throw new BusinessException("PAYMENT_TERM_NOT_FOUND", "付款条款不存在");
        ctContractPaymentTermMapper.updateById(term);
    }

    @Transactional
    public void delete(Long id) {
        ctContractPaymentTermMapper.deleteById(id);
    }
}
