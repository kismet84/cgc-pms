package com.cgcpms.contract;

import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.service.ContractProcurementPayableService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractProcurementPayableServiceTest {
    @Mock CtContractMapper contractMapper;
    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks ContractProcurementPayableService service;

    @Test
    void payableUsesApprovedReceiptsMinusConfirmedQualifiedReturns() {
        CtContract contract = new CtContract();
        contract.setId(1L);
        contract.setTenantId(7L);
        contract.setContractType("PURCHASE");
        when(contractMapper.selectByIdForUpdate(1L, 7L)).thenReturn(contract);
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(7L), eq(1L)))
                .thenReturn(new BigDecimal("120.00"), new BigDecimal("20.00"));

        assertEquals(0, new BigDecimal("100.00").compareTo(service.recalculate(1L, 7L)));
        assertEquals(0, new BigDecimal("100.00").compareTo(contract.getPayableAmount()));
        verify(contractMapper).updateById(contract);
    }
}
