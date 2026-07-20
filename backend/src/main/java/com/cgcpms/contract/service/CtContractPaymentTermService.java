package com.cgcpms.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.mapper.CtContractPaymentTermMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
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

    @Transactional(rollbackFor = Exception.class)
    public Long create(CtContractPaymentTerm term) {
        requireDraftParentContract(term.getContractId());
        term.setTenantId(UserContext.getCurrentTenantId());
        ctContractPaymentTermMapper.insert(term);
        return term.getId();
    }

    /**
     * 批量保存付款条款（全量替换模式）。
     * <p>
     * 数据完整性说明：此方法遵循"先删后插"的全量替换策略。
     * <ol>
     *   <li>先物理删除合同下所有旧条款（含已被软删除的记录，确保主键不冲突）</li>
     *   <li>再逐条插入新条款列表（如果非空）</li>
     * </ol>
     * 调用方应保证 newTerms 包含完整的最新条款集合，避免数据丢失。
     * <p>
     * 使用逐条 {@code ctContractPaymentTermMapper.insert()} 而非 {@code Db.saveBatch()}：
     * MyBatis-Plus 的 {@code Db.saveBatch()} 内部使用 JDBC batch executor，
     * 在 MySQL 下正常但在 H2 内存数据库并行测试中易触发表锁超时（H2 行锁粒度较粗）。
     * 付款条款通常 ≤10 条/合同，逐条插入性能差异可忽略。
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchSave(Long contractId, List<CtContractPaymentTerm> newTerms) {
        requireDraftParentContract(contractId);
        LambdaQueryWrapper<CtContractPaymentTerm> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(CtContractPaymentTerm::getContractId, contractId);
        ctContractPaymentTermMapper.delete(deleteWrapper);
        if (newTerms != null && !newTerms.isEmpty()) {
            Long tenantId = UserContext.getCurrentTenantId();
            for (CtContractPaymentTerm t : newTerms) {
                t.setId(null);            // 清空ID，让ASSIGN_ID自动生成新ID，避免与软删除记录主键冲突
                t.setContractId(contractId);
                t.setTenantId(tenantId);
                ctContractPaymentTermMapper.insert(t);
            }
        } else {
            log.info("batchSave: deleted all payment terms for contract {}, no new terms to insert", contractId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
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

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long contractId, Long id) {
        requireDraftParentContract(contractId);
        CtContractPaymentTerm existing = ctContractPaymentTermMapper.selectById(id);
        if (existing == null || !existing.getContractId().equals(contractId))
            throw new BusinessException("PAYMENT_TERM_NOT_FOUND", "付款条款不存在");
        ctContractPaymentTermMapper.deleteById(id);
    }
}
