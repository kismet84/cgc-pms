package com.cgcpms.contract.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContractProcurementPayableService {
    private final CtContractMapper contractMapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(rollbackFor = Exception.class)
    public BigDecimal recalculate(Long contractId, Long tenantId) {
        CtContract contract = contractMapper.selectByIdForUpdate(contractId, tenantId);
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)
                || !"PURCHASE".equals(contract.getContractType())) {
            throw new BusinessException("PURCHASE_CONTRACT_NOT_FOUND", "采购合同不存在");
        }
        BigDecimal receipts = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(i.amount),0)
                FROM mat_receipt r
                JOIN mat_receipt_item i ON i.tenant_id=r.tenant_id AND i.receipt_id=r.id AND i.deleted_flag=0
                WHERE r.tenant_id=? AND r.contract_id=? AND r.approval_status='APPROVED' AND r.deleted_flag=0
                """, BigDecimal.class, tenantId, contractId);
        BigDecimal returns = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(i.amount),0)
                FROM sp_supplier_return r
                JOIN sp_supplier_return_item i ON i.tenant_id=r.tenant_id AND i.return_id=r.id
                WHERE r.tenant_id=? AND r.contract_id=? AND r.status='CONFIRMED'
                  AND i.return_source='QUALIFIED' AND r.deleted_flag=0 AND i.deleted_flag=0
                """, BigDecimal.class, tenantId, contractId);
        BigDecimal payable = nvl(receipts).subtract(nvl(returns)).setScale(2);
        contract.setPayableAmount(payable);
        contractMapper.updateById(contract);
        return payable;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
