package com.cgcpms.contract.service;

import static com.cgcpms.common.util.BigDecimalUtils.nvl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Set;

final class CtContractPerformanceSettlement {

    private final CtContractMapper ctContractMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final JdbcTemplate jdbcTemplate;

    CtContractPerformanceSettlement(CtContractMapper ctContractMapper,
                                    ProjectAccessChecker projectAccessChecker,
                                    JdbcTemplate jdbcTemplate) {
        this.ctContractMapper = ctContractMapper;
        this.projectAccessChecker = projectAccessChecker;
        this.jdbcTemplate = jdbcTemplate;
    }

    void settlePerformance(Long contractId, Integer clientVersion) {
        CtContract contract = ctContractMapper.selectOne(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getId, contractId)
                .eq(CtContract::getTenantId, UserContext.getCurrentTenantId())
                .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        if (contract == null) throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        projectAccessChecker.checkAccess(contract.getProjectId(), "结清合同履约");
        CtContractService.ensureClientVersionMatches(clientVersion, contract.getVersion());
        if (!ContractStatusConstants.APPROVAL_APPROVED.equals(contract.getApprovalStatus())
                || !ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus())) {
            throw new BusinessException("CONTRACT_SETTLEMENT_STATE_INVALID", "只有审批通过且履约中的合同可以结清");
        }
        requireSettlementFact(contract);
        int updated = ctContractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                .eq(CtContract::getId, contractId)
                .eq(CtContract::getTenantId, contract.getTenantId())
                .eq(CtContract::getVersion, contract.getVersion())
                .eq(CtContract::getContractStatus, ContractStatusConstants.STATUS_PERFORMING)
                .set(CtContract::getContractStatus, ContractStatusConstants.STATUS_SETTLED)
                .set(CtContract::getVersion, contract.getVersion() + 1));
        if (updated != 1) throw new BusinessException("CONTRACT_VERSION_CONFLICT", "合同已被其他用户修改，请刷新后重试");
    }

    private void requireSettlementFact(CtContract contract) {
        Long tenantId = contract.getTenantId();
        Long contractId = contract.getId();
        String type = contract.getContractType();
        if ("MAIN".equals(type)) {
            requirePositiveCount("""
                    SELECT COUNT(*) FROM owner_settlement
                    WHERE tenant_id=? AND contract_id=? AND settlement_type='FINAL'
                      AND status='RECEIVABLE_CREATED' AND deleted_flag=0
                    """, tenantId, contractId);
            return;
        }
        if (Set.of("SUB", "SUBCONTRACT").contains(type)) {
            requirePositiveCount("""
                    SELECT COUNT(*) FROM stl_settlement
                    WHERE tenant_id=? AND contract_id=? AND settlement_type='FINAL'
                      AND approval_status='APPROVED' AND settlement_status='FINALIZED' AND deleted_flag=0
                    """, tenantId, contractId);
            return;
        }
        if ("PURCHASE".equals(type)) {
            requirePositiveCount("SELECT COUNT(*) FROM mat_purchase_order WHERE tenant_id=? AND contract_id=? AND deleted_flag=0",
                    tenantId, contractId);
            Integer openOrders = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM mat_purchase_order
                    WHERE tenant_id=? AND contract_id=? AND order_status NOT IN ('COMPLETED','CANCELLED')
                      AND deleted_flag=0
                    """, Integer.class, tenantId, contractId);
            BigDecimal payable = contract.getPayableAmount();
            BigDecimal paid = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(SUM(pay_amount),0) FROM pay_record
                    WHERE tenant_id=? AND contract_id=? AND pay_status='SUCCESS' AND deleted_flag=0
                    """, BigDecimal.class, tenantId, contractId);
            if ((openOrders != null && openOrders > 0) || payable == null
                    || nvl(paid).compareTo(payable) < 0) {
                throw settlementFactRequired();
            }
            return;
        }
        throw settlementFactRequired();
    }

    private void requirePositiveCount(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        if (count == null || count == 0) throw settlementFactRequired();
    }

    private BusinessException settlementFactRequired() {
        return new BusinessException("CONTRACT_SETTLEMENT_FACT_REQUIRED", "缺少合同类型对应的权威终结事实，禁止结清");
    }

}
